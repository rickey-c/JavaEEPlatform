package cn.edu.xmu.javaee.productdemoaop.service;

import cn.edu.xmu.javaee.core.exception.BusinessException;
import cn.edu.xmu.javaee.productdemoaop.dao.ProductDao;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.Product;
import cn.edu.xmu.javaee.productdemoaop.dao.bo.User;
import cn.edu.xmu.javaee.productdemoaop.util.RedisUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

@Service
public class ProductService {

    private Logger logger = LoggerFactory.getLogger(ProductService.class);

    @Resource
    private RedisUtil redisUtil;

    private ProductDao productDao;

    private final String PRODUCT_CACHE_KEY = "product:";

    private final Long CACHE_TIME_OUT = 100L;
    

    @Autowired
    public ProductService(ProductDao productDao) {
        this.productDao = productDao;
    }

    /**
     * 获取某个商品信息，仅展示相关内容
     *
     * @param id 商品id
     * @return 商品对象
     */
    @Transactional(rollbackFor = {BusinessException.class})
    public Product retrieveProductByID(Long id, boolean all,boolean useRedis) throws BusinessException {
        logger.debug("findProductById: id = {}, all = {}", id, all);
        if (useRedis){
            String cacheKey = PRODUCT_CACHE_KEY + id;
            // 查缓存
            Product product = (Product)redisUtil.get(cacheKey);
            if (product!=null){
                logger.info("Hit product: key = {}",cacheKey);
                return product;
            }
            // 查数据库
            product = productDao.retrieveProductByID(id, all, true);
            if (product!=null){
                // 存入缓存
                boolean setResult = redisUtil.set(cacheKey, product, CACHE_TIME_OUT);
                logger.info("Store product: key = {},result = {}",cacheKey,setResult);
            }
            return product;
        }
        else {
            // 查数据库
            Product product = productDao.retrieveProductByID(id, all, false);
            return product;
        }
        // return productDao.retrieveProductByID(id, all);
    }

    /**
     * 用商品名称搜索商品
     *
     * @param name 商品名称
     * @return 商品对象
     */
    @Transactional
    public List<Product> retrieveProductByName(String name, boolean all) throws BusinessException{
        return productDao.retrieveProductByName(name, all);
    }

    /**
     * 新增商品
     * @param product 新商品信息
     * @return 新商品
     */
    @Transactional
    public Product createProduct(Product product, User user) throws BusinessException{
        return productDao.createProduct(product, user);
    }


    /**
     * 修改商品
     * @param product 修改商品信息
     */
    @Transactional
    public void modifyProduct(Product product, User user) throws BusinessException{
        productDao.modiProduct(product, user);
    }

    /** 删除商品
     * @param id 商品id
     * @return 删除是否成功
     */
    @Transactional
    public void deleteProduct(Long id) throws BusinessException {
        productDao.deleteProduct(id);
    }

    @Transactional
    public Product findProductById_manual(Long id) throws BusinessException {
        logger.debug("findProductById_manual: id = {}", id);
        return productDao.findProductByID_manual(id);
    }

    /**
     * 用商品名称搜索商品
     *
     * @param name 商品名称
     * @return 商品对象
     */
    @Transactional
    public List<Product> findProductByName_manual(String name) throws BusinessException{
        return productDao.findProductByName_manual(name);
    }

}
