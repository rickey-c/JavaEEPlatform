//School of Informatics Xiamen University, GPL-3.0 license
package cn.edu.xmu.javaee.productdemoaop.dao;

import cn.edu.xmu.javaee.core.exception.BusinessException;
import cn.edu.xmu.javaee.core.model.ReturnNo;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.OnSale;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.Product;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.User;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.ProductPoMapper;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.po.ProductPo;
import cn.edu.xmu.javaee.productdemoaop.mapper.generator.po.ProductPoExample;
import cn.edu.xmu.javaee.productdemoaop.mapper.manual.ProductAllMapper;
import cn.edu.xmu.javaee.productdemoaop.mapper.manual.po.ProductAllPo;
import cn.edu.xmu.javaee.productdemoaop.util.CloneFactory;
import cn.edu.xmu.javaee.productdemoaop.util.RedisUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ming Qiu
 **/
@Repository
public class ProductDao {

    private final static Logger logger = LoggerFactory.getLogger(ProductDao.class);

    @Resource
    private RedisUtil redisUtil;

    private ProductPoMapper productPoMapper;

    private OnSaleDao onSaleDao;

    private ProductAllMapper productAllMapper;

    private final String PRODUCT_PO_CACHE_KEY = "productPo:";

    private final String PRODUCT_ON_SALE_LIST_CACHE_KEY = "productOnSaleList:";

    private final String PRODUCT_OTHER_LIST_CACHE_KEY = "productOtherList:";

    private final String PRODUCT_CACHE_KEY = "product:";

    private final Long CACHE_TIME_OUT = 100L;

    @Autowired
    public ProductDao(ProductPoMapper productPoMapper, OnSaleDao onSaleDao, ProductAllMapper productAllMapper) {
        this.productPoMapper = productPoMapper;
        this.onSaleDao = onSaleDao;
        this.productAllMapper = productAllMapper;
    }

    /**
     * 用GoodsPo对象找Goods对象
     *
     * @param name
     * @return Goods对象列表，带关联的Product返回
     */
    public List<Product> retrieveProductByName(String name, boolean all) throws BusinessException {
        List<Product> productList = new ArrayList<>();
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andNameEqualTo(name);
        List<ProductPo> productPoList = productPoMapper.selectByExample(example);
        for (ProductPo po : productPoList) {
            Product product = null;
            if (all) {
                product = this.retrieveFullProduct(po,false);
            } else {
                product = CloneFactory.copy(new Product(), po);
            }
            productList.add(product);
        }
        logger.debug("retrieveProductByName: productList = {}", productList);
        return productList;
    }

    /**
     * 用GoodsPo对象找Goods对象
     *
     * @param productId 产品ID
     * @param all 是否查询完整信息
     * @param useRedis 是否使用缓存
     * @return 返回商品对象列表，带关联的Product返回
     * @throws BusinessException 如果产品ID不存在
     */
    public Product retrieveProductByID(Long productId, boolean all, boolean useRedis) throws BusinessException {

        logger.debug("findProductById: id = {}, all = {}", productId, all);
        Product product = null;
        ProductPo productPo = null;
        if (useRedis){
            // 使用缓存
            String cacheKey = PRODUCT_CACHE_KEY + productId;
            // 1. 查缓存，完整的product
            product = (Product)redisUtil.get(cacheKey);
            if (product!=null){
                logger.info("Hit product: key = {}",cacheKey);
                return product;
            }else {
                // 缓存中查不到完整的product
                cacheKey = PRODUCT_PO_CACHE_KEY + productId;
                // 2. 查缓存，查询productPo
                productPo = (ProductPo) redisUtil.get(cacheKey);
                // 缓存命中
                if (productPo != null) {
                    logger.info("Hit productPo: key = {}", cacheKey);
                } else {
                    // 缓存不命中 -> 查数据库
                    productPo = productPoMapper.selectByPrimaryKey(productId);
                    if (productPo == null) {
                        throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST, "产品id不存在");
                    }
                    // 2. 存入productPo
                    boolean setResult = redisUtil.set(cacheKey, productPo, CACHE_TIME_OUT);
                    logger.info("Store productPo: key = {}, result = {}", cacheKey, setResult);
                }
                // 根据是否需要完整信息决定是否查关联信息
                if (all) {
                    product = this.retrieveFullProduct(productPo, true);
                    // 1. 存入product
                    cacheKey = PRODUCT_CACHE_KEY + productId;
                    redisUtil.set(cacheKey,product,CACHE_TIME_OUT);
                } else {
                    product = CloneFactory.copy(new Product(), productPo);
                }
            }
        }else {
            // 不使用Redis，直接从数据库查询
            productPo = productPoMapper.selectByPrimaryKey(productId);
            if (productPo == null) {
                throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST, "产品id不存在");
            }
            // 根据是否需要完整信息决定是否查关联信息
            if (all) {
                product = this.retrieveFullProduct(productPo, false);
            } else {
                product = CloneFactory.copy(new Product(), productPo);
            }
        }
        logger.debug("retrieveProductByID: product = {}", product);
        return product;
    }

    /**
     * 获取完整的产品信息，包括关联的产品和在售产品
     *
     * @param productPo 产品Po对象
     * @param useRedis 是否使用缓存
     * @return 完整的产品对象
     * @throws DataAccessException 数据访问异常
     */
    private Product retrieveFullProduct(ProductPo productPo, Boolean useRedis) throws DataAccessException {
        assert productPo != null;
        Product product = CloneFactory.copy(new Product(), productPo);

        if (useRedis) {
            // 3.查询在售列表缓存
            String onSaleListCacheKey = PRODUCT_ON_SALE_LIST_CACHE_KEY + productPo.getId();
            List<OnSale> latestOnSale = (List<OnSale>) redisUtil.get(onSaleListCacheKey);
            if (latestOnSale != null) {
                logger.info("Hit onSaleListCache: key = {}", onSaleListCacheKey);
            } else {
                // 查询缓存为空，则去数据库查找，并存入缓存
                latestOnSale = onSaleDao.getLatestOnSale(productPo.getId());
                boolean result = redisUtil.set(onSaleListCacheKey, (Serializable) latestOnSale, CACHE_TIME_OUT);
                logger.info("Store onSaleListCache: key = {}, result = {}", onSaleListCacheKey, result);
            }
            product.setOnSaleList(latestOnSale);

            // 4.查询其他产品缓存
            String otherProductCacheKey = PRODUCT_OTHER_LIST_CACHE_KEY + productPo.getId();
            List<Product> otherProduct = (List<Product>) redisUtil.get(otherProductCacheKey);
            if (otherProduct != null) {
                logger.info("Hit otherProductCache: key = {}", otherProductCacheKey);
            } else {
                // 查询缓存为空，则去数据库查找，并存入缓存
                otherProduct = this.retrieveOtherProduct(productPo);
                boolean result = redisUtil.set(otherProductCacheKey, (Serializable) otherProduct, CACHE_TIME_OUT);
                logger.info("Store OtherProductCache: key = {}, result = {}", otherProductCacheKey, result);
            }
            product.setOtherProduct(otherProduct);

        } else {
            // 不使用Redis，直接从数据库查询
            List<OnSale> latestOnSale = onSaleDao.getLatestOnSale(productPo.getId());
            product.setOnSaleList(latestOnSale);

            List<Product> otherProduct = this.retrieveOtherProduct(productPo);
            product.setOtherProduct(otherProduct);
        }

        return product;
    }

    private List<Product> retrieveOtherProduct(ProductPo productPo) throws DataAccessException {
        assert productPo != null;
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andGoodsIdEqualTo(productPo.getGoodsId());
        criteria.andIdNotEqualTo(productPo.getId());
        List<ProductPo> productPoList = productPoMapper.selectByExample(example);
        return productPoList.stream().map(po -> CloneFactory.copy(new Product(), po)).collect(Collectors.toList());
    }

    /**
     * 创建Goods对象
     *
     * @param product 传入的Goods对象
     * @return 返回对象ReturnObj
     */
    public Product createProduct(Product product, User user) throws BusinessException {

        Product retObj = null;
        product.setCreator(user);
        product.setGmtCreate(LocalDateTime.now());
        ProductPo po = CloneFactory.copy(new ProductPo(), product);
        int ret = productPoMapper.insertSelective(po);
        retObj = CloneFactory.copy(new Product(), po);
        return retObj;
    }

    /**
     * 修改商品信息
     *
     * @param product 传入的product对象
     * @return void
     */
    public void modiProduct(Product product, User user) throws BusinessException {
        product.setGmtModified(LocalDateTime.now());
        product.setModifier(user);
        ProductPo po = CloneFactory.copy(new ProductPo(), product);
        int ret = productPoMapper.updateByPrimaryKeySelective(po);
        if (ret == 0) {
            throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST);
        }
    }

    /**
     * 删除商品，连带规格
     *
     * @param id 商品id
     * @return
     */
    public void deleteProduct(Long id) throws BusinessException {
        int ret = productPoMapper.deleteByPrimaryKey(id);
        if (ret == 0) {
            throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST);
        }
    }

    public List<Product> findProductByName_manual(String name) throws BusinessException {
        List<Product> productList;
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andNameEqualTo(name);
        List<ProductAllPo> productPoList = productAllMapper.getProductWithAll(example);
        productList = productPoList.stream().map(o -> CloneFactory.copy(new Product(), o)).collect(Collectors.toList());
        logger.debug("findProductByName_manual: productList = {}", productList);
        return productList;
    }

    /**
     * 用GoodsPo对象找Goods对象
     *
     * @param productId
     * @return Goods对象列表，带关联的Product返回
     */
    public Product findProductByID_manual(Long productId) throws BusinessException {
        Product product = null;
        ProductPoExample example = new ProductPoExample();
        ProductPoExample.Criteria criteria = example.createCriteria();
        criteria.andIdEqualTo(productId);
        List<ProductAllPo> productPoList = productAllMapper.getProductWithAll(example);

        if (productPoList.size() == 0) {
            throw new BusinessException(ReturnNo.RESOURCE_ID_NOTEXIST, "产品id不存在");
        }
        product = CloneFactory.copy(new Product(), productPoList.get(0));
        logger.debug("findProductByID_manual: product = {}", product);
        return product;
    }
}
