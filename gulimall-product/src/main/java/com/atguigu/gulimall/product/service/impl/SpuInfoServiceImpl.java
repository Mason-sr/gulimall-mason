package com.atguigu.gulimall.product.service.impl;

import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SpuInfoDescService spuInfoDescService;

    @Autowired
    SpuImagesService imagesService;

    @Autowired
    AttrService attrService;

    @Autowired
    ProductAttrValueService attrValueService;

    @Autowired
    SkuInfoService skuInfoService;
    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    CouponFeignService couponFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * //TODO 高级部分完善
     * @param vo
     * 因为是一个大保存，要保存很多东西，需要用到事务
     */
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {
        //前端发送的数据 SpuSaveVo中的所有信息，一一对应

        //1、保存spu基本信息 pms_spu_info
        //   infoEntity是和数据库是一一对应的
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        //   这些数据都是来自SpuSaveVo里面的，将vo里面的数据拷贝到infoEntity中
        BeanUtils.copyProperties(vo,infoEntity);
        //   区别  infoEntity &  vo 哪些字段不一样，赋默认值 ，其中  createTime 和 updateTime Entity中没有
        infoEntity.setCreateTime(new Date()); //当前的时间
        infoEntity.setUpdateTime(new Date());
        this.saveBaseSpuInfo(infoEntity);


        //2、保存Spu的描述图片 pms_spu_info_desc    SpuInfoDescEntity只有两个属性 spuId   descript
        //   pms_spu_info_desc表的id不是自增的，spu_id拿过来 ，还有一个属性   descript
        List<String> decript = vo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        //只有在上一步保存了基本信息才能知道  spu_id
        descEntity.setSpuId(infoEntity.getId());
        //这里面的decript就是vo.getDecript  用逗号来分割里面的每一个元素
        descEntity.setDecript(String.join(",",decript));
        //保存描述信息，新建方法，controller-->service--->serviceImpl其实就是在dao层中插入数据就可以了
        spuInfoDescService.saveSpuInfoDesc(descEntity);



        //3、保存spu的图片集 pms_spu_images
        //   表pms_spu_images中的字段 ====>id  spu_id img_name img_url img_sort default_name
        List<String> images = vo.getImages();
        //新建方法saveImages
        imagesService.saveImages(infoEntity.getId(),images);


        //4、保存spu的规格参数; pms_product_attr_value,
        //   表  pms_product_attr_value的属性     spu_id id attr_id attr_name attr_value attr_sort quick_show
        //   来自页面，全部放在 List<BaseAttrs> baseAttrs中
        //   baseAttrs中  三个属性  Long attrId ,String attrValues, showDesc
        //   保存基本属性
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        //遍历这些属性，每一个attr都可以收集成pms_product_attr_value表对应的实体类
        List<ProductAttrValueEntity> collect = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            valueEntity.setAttrId(attr.getAttrId());
            //因为页面没有发送id的名字过来需要获取一下
            AttrEntity id = attrService.getById(attr.getAttrId());
            valueEntity.setAttrName(id.getAttrName());
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setQuickShow(attr.getShowDesc());
            valueEntity.setSpuId(infoEntity.getId());
            return valueEntity;
        }).collect(Collectors.toList());
        //将构造好的方法传给它
        attrValueService.saveProductAttr(collect);


        //5、保存spu的积分信息；gulimall_sms->sms_spu_bounds
        //   在springcloud中 A服务给B服务传递数据，将这些数据封装成对象，springcloud会将其转成json
        //   B就会收到json数据，B再将json变化成json数据，可以说是TO过程=======>准备一个TO，在common中SpuBoundTo
        //   远程调用服务，couponFeignService
        //   vo当中有一个bound,bounds里面有 grow_bounds buy_bounds，还需要提交spu_id才算一个完整的数据
        Bounds bounds = vo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds,spuBoundTo);
        spuBoundTo.setSpuId(infoEntity.getId());
        //希望couponFeignService有一个saveSpuBounds方法，将我们相关的信息传递过去，传递给远程服务进行调用
        //  传递相关信息包括： spu_id grow_bounds buy_bounds ---->传递给远程服务调用
        R r = couponFeignService.saveSpuBounds(spuBoundTo);
        if(r.getCode() != 0){
            log.error("远程保存spu积分信息失败");
        }


        //5、保存当前spu对应的所有sku信息；spu:某一款 sku某一件
        List<Skus> skus = vo.getSkus();
        if(skus!=null && skus.size()>0){
            skus.forEach(item->{
                String defaultImg = "";
                //某一个Images 里面的defaultImg字段是ture，说明就是默认图片
                for (Images image : item.getImages()) {
                    if(image.getDefaultImg() == 1){
                        defaultImg = image.getImgUrl();
                    }
                }
                // 只有这四个属性在Skus(前端传来的数据)中是有的skuName price skuTitle skuSubtitle,拷贝进去了
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item,skuInfoEntity);
                skuInfoEntity.setBrandId(infoEntity.getBrandId());//品牌id
                skuInfoEntity.setCatalogId(infoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSpuId(infoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                skuInfoService.saveSkuInfo(skuInfoEntity);

                //5.1）、sku的基本信息；pms_sku_info
                //saveSkuInfo保存完之后，它的自增主键就出来了
                Long skuId = skuInfoEntity.getSkuId();//自增主键
                //保存skuImageEntity
                List<SkuImagesEntity> imagesEntities = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    //所有和sku相关的都要保存skuId
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity->{
                    //返回true就是需要，false就是剔除
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                //5.2）、sku的图片信息；pms_sku_image
                skuImagesService.saveBatch(imagesEntities);//保存所有的图片，批量保存数据
                //TODO 没有图片路径的无需保存

                //5.3）、sku的销售属性信息：pms_sku_sale_attr_value
                //      数据库表pms_sku_sale_attr_value中属性有   id  sku_id attr_id  attr_name attr_value attr_sort
                //      从item中获取，前端中，getAttr获取attrId,attrName,attrValue，有对应的可以直接对拷
                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity attrValueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(a, attrValueEntity);
                    //主要关心关联的哪个sku
                    attrValueEntity.setSkuId(skuId);
                    return attrValueEntity;
                }).collect(Collectors.toList());
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                // //5.4）、sku的优惠、满减等信息；gulimall_sms->sms_sku_ladder\sms_sku_full_reduction\sms_member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item,skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                if(skuReductionTo.getFullCount() >0 || skuReductionTo.getFullPrice().compareTo(new BigDecimal("0")) == 1){
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if(r1.getCode() != 0){
                        log.error("远程保存sku优惠信息失败");
                    }
                }
            });
        }

    }

    @Override
    public void saveBaseSpuInfo(SpuInfoEntity infoEntity) {
        // this.baseMapper ===>  拿到SpuInfoEntity 的Dao
        this.baseMapper.insert(infoEntity);
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {

        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if(!StringUtils.isEmpty(key)){
            wrapper.and((w)->{
                w.eq("id",key).or().like("spu_name",key);
            });
        }
        // status=1 and (id=1 or spu_name like xxx)
        String status = (String) params.get("status");
        if(!StringUtils.isEmpty(status)){
            wrapper.eq("publish_status",status);
        }

        String brandId = (String) params.get("brandId");
        if(!StringUtils.isEmpty(brandId)&&!"0".equalsIgnoreCase(brandId)){
            wrapper.eq("brand_id",brandId);
        }

        String catelogId = (String) params.get("catelogId");
        if(!StringUtils.isEmpty(catelogId)&&!"0".equalsIgnoreCase(catelogId)){
            wrapper.eq("catalog_id",catelogId);
        }

        /**
         * status: 2
         * key:
         * brandId: 9
         * catelogId: 225
         */

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }


}