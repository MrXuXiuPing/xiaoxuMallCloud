package com.mallplus.order.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mallplus.common.constant.AllEnum;
import com.mallplus.common.constant.OrderStatus;
import com.mallplus.common.entity.oms.*;
import com.mallplus.common.entity.pms.*;
import com.mallplus.common.entity.sms.*;
import com.mallplus.common.entity.ums.UmsIntegrationConsumeSetting;
import com.mallplus.common.entity.ums.UmsMember;
import com.mallplus.common.entity.ums.UmsMemberBlanceLog;
import com.mallplus.common.exception.ApiMallPlusException;
import com.mallplus.common.exception.BusinessException;
import com.mallplus.common.feign.MarkingFeignClinent;
import com.mallplus.common.feign.MemberFeignClient;
import com.mallplus.common.feign.PmsFeignClinent;
import com.mallplus.common.redis.constant.RedisToolsConstant;
import com.mallplus.common.redis.template.RedisUtil;
import com.mallplus.common.utils.CommonResult;
import com.mallplus.common.utils.DateUtils;
import com.mallplus.common.utils.JsonUtil;
import com.mallplus.common.utils.ValidatorUtils;
import com.mallplus.common.vo.*;
import com.mallplus.order.config.WxAppletProperties;
import com.mallplus.order.mapper.OmsCartItemMapper;
import com.mallplus.order.mapper.OmsOrderMapper;
import com.mallplus.order.mapper.OmsOrderOperateHistoryMapper;
import com.mallplus.order.mapper.OmsOrderSettingMapper;
import com.mallplus.order.service.*;
import com.mallplus.order.utils.applet.TemplateData;
import com.mallplus.order.utils.applet.WX_TemplateMsgUtil;
import com.mallplus.order.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * ????????? ???????????????
 * </p>
 *
 * @author zscat
 * @since 2019-04-17
 */
@Slf4j
@Service
public class OmsOrderServiceImpl extends ServiceImpl<OmsOrderMapper, OmsOrder> implements IOmsOrderService {

    @Autowired
    private WxAppletProperties wxAppletProperties;
    @Resource
    private WechatApiService wechatApiService;

    @Resource
    private IUmsMemberReceiveAddressService addressService;
    @Resource
    private OmsOrderMapper orderMapper;
    @Resource
    private RedisUtil redisUtil;
//    @Resource
//    private IOmsOrderOperateHistoryService orderOperateHistoryDao;
    @Resource
    private OmsOrderOperateHistoryMapper orderOperateHistoryMapper;
    @Resource
    private PmsFeignClinent pmsFeignClinent;
    @Resource
    private MemberFeignClient memberFeignClient;
    @Resource
    private MarkingFeignClinent markingFeignClinent;
    @Resource
    private OmsCartItemMapper cartItemMapper;

    @Resource
    private IOmsOrderService orderService;
    @Resource
    private IOmsOrderItemService orderItemService;

    @Resource
    private OmsOrderSettingMapper orderSettingMapper;
    @Resource
    private   IOmsCartItemService cartItemService;
    @Autowired
    private IOmsOrderOperateHistoryService orderOperateHistoryService;
    @Autowired
    private ApiContext apiContext;

    @Override
    public int delivery(List<OmsOrderDeliveryParam> deliveryParamList) {
        //????????????
        int count = orderMapper.delivery(deliveryParamList);
        //??????????????????
        List<OmsOrderOperateHistory> operateHistoryList = deliveryParamList.stream()
                .map(omsOrderDeliveryParam -> {
                    OmsOrderOperateHistory history = new OmsOrderOperateHistory();
                    history.setOrderId(omsOrderDeliveryParam.getOrderId());
                    history.setCreateTime(new Date());
                    history.setOperateMan("???????????????");
                    history.setOrderStatus(2);
                    history.setNote("????????????");
                    return history;
                }).collect(Collectors.toList());
        orderOperateHistoryService.saveBatch(operateHistoryList);
        return count;
    }

    @Override
    public int close(List<Long> ids, String note) {
        OmsOrder record = new OmsOrder();
        record.setStatus(4);
        int count = orderMapper.update(record, new QueryWrapper<OmsOrder>().eq("delete_status",0).in("id",ids));
        List<OmsOrderOperateHistory> historyList = ids.stream().map(orderId -> {
            OmsOrderOperateHistory history = new OmsOrderOperateHistory();
            history.setOrderId(orderId);
            history.setCreateTime(new Date());
            history.setOperateMan("???????????????");
            history.setOrderStatus(4);
            history.setNote("????????????:" + note);
            return history;
        }).collect(Collectors.toList());
        orderOperateHistoryService.saveBatch(historyList);
        return count;
    }
    @Override
    public int updateReceiverInfo(OmsReceiverInfoParam receiverInfoParam) {
        OmsOrder order = new OmsOrder();
        order.setId(receiverInfoParam.getOrderId());
        order.setReceiverName(receiverInfoParam.getReceiverName());
        order.setReceiverPhone(receiverInfoParam.getReceiverPhone());
        order.setReceiverPostCode(receiverInfoParam.getReceiverPostCode());
        order.setReceiverDetailAddress(receiverInfoParam.getReceiverDetailAddress());
        order.setReceiverProvince(receiverInfoParam.getReceiverProvince());
        order.setReceiverCity(receiverInfoParam.getReceiverCity());
        order.setReceiverRegion(receiverInfoParam.getReceiverRegion());
        order.setModifyTime(new Date());
        int count = orderMapper.updateById(order);
        //??????????????????
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(receiverInfoParam.getOrderId());
        history.setCreateTime(new Date());
        history.setOperateMan("???????????????");
        history.setOrderStatus(receiverInfoParam.getStatus());
        history.setNote("?????????????????????");
        orderOperateHistoryMapper.insert(history);
        return count;
    }

    @Override
    public int updateMoneyInfo(OmsMoneyInfoParam moneyInfoParam) {
        OmsOrder order = new OmsOrder();
        order.setId(moneyInfoParam.getOrderId());
        order.setFreightAmount(moneyInfoParam.getFreightAmount());
        order.setDiscountAmount(moneyInfoParam.getDiscountAmount());
        order.setModifyTime(new Date());
        int count = orderMapper.updateById(order);
        //??????????????????
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(moneyInfoParam.getOrderId());
        history.setCreateTime(new Date());
        history.setOperateMan("???????????????");
        history.setOrderStatus(moneyInfoParam.getStatus());
        history.setNote("??????????????????");
        orderOperateHistoryMapper.insert(history);
        return count;
    }

    @Override
    public int updateNote(Long id, String note, Integer status) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setNote(note);
        order.setModifyTime(new Date());
        int count = orderMapper.updateById(order);
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(id);
        history.setCreateTime(new Date());
        history.setOperateMan("???????????????");
        history.setOrderStatus(status);
        history.setNote("?????????????????????" + note);
        orderOperateHistoryMapper.insert(history);
        return count;
    }


    /**
     * ?????????????????????????????????
     */
    private ConfirmOrderResult.CalcAmount calcCartAmount(List<OmsCartItem> cartPromotionItemList ,BigDecimal transFee,BigDecimal payAmount) {
        ConfirmOrderResult.CalcAmount calcAmount = new ConfirmOrderResult.CalcAmount();
        calcAmount.setFreightAmount(transFee);

        BigDecimal totalAmount = new BigDecimal("0");
        BigDecimal promotionAmount = new BigDecimal("0");
        CartMarkingVo vo = new CartMarkingVo();
        vo.setCartList(cartPromotionItemList);
        SmsBasicMarking basicMarking = markingFeignClinent.matchOrderBasicMarking(vo);
        log.info("basicMarking="+ com.alibaba.fastjson.JSONObject.toJSONString(basicMarking));
        if (basicMarking!=null){
            promotionAmount = basicMarking.getMinAmount();
        }
        if (promotionAmount==null){
            promotionAmount=BigDecimal.ZERO;
        }
        for (OmsCartItem cartPromotionItem : cartPromotionItemList) {
            totalAmount = totalAmount.add(cartPromotionItem.getPrice().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
            //  promotionAmount = promotionAmount.add(cartPromotionItem.getReduceAmount().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
        }
        calcAmount.setTotalAmount(totalAmount);
        calcAmount.setPromotionAmount(promotionAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) > 0) {
            calcAmount.setPayAmount(payAmount.subtract(promotionAmount).add(transFee));
        } else {
            calcAmount.setPayAmount(totalAmount.subtract(promotionAmount).add(transFee));
        }
        if (calcAmount.getPayAmount().compareTo(BigDecimal.ZERO)<0){
            calcAmount.setPayAmount(new BigDecimal("0.01"));
        }
        return calcAmount;
    }

    /**
     * ???????????????????????????????????????
     *
     * @param couponId  ?????????id
     * @param memberId  ??????id
     * @param useStatus 0->????????????1->?????????
     */
    private void updateCouponStatus(Long couponId, Long memberId, Integer useStatus) {
        if (couponId == null) {
            return;
        }
        //????????????????????????
        SmsCouponHistory queryC = new SmsCouponHistory();
        queryC.setCouponId(couponId);
        if (useStatus == 0) {
            queryC.setUseStatus(1);
        } else {
            queryC.setUseStatus(0);
        }
        List<SmsCouponHistory> couponHistoryList = markingFeignClinent.listCouponHistory(queryC);
        if (!CollectionUtils.isEmpty(couponHistoryList)) {
            SmsCouponHistory couponHistory = couponHistoryList.get(0);
            couponHistory.setUseTime(new Date());
            couponHistory.setUseStatus(useStatus);
            markingFeignClinent.updateCouponHistoryById(couponHistory);
        }
    }
    /**
     * ????????????????????????
     */
    private String getOrderPromotionInfo(List<OmsOrderItem> orderItemList) {
        StringBuilder sb = new StringBuilder();
        for (OmsOrderItem orderItem : orderItemList) {
            sb.append(orderItem.getPromotionName());
            sb.append(",");
        }
        String result = sb.toString();
        if (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
    private void handleRealAmount(List<OmsOrderItem> orderItemList) {
        for (OmsOrderItem orderItem : orderItemList) {
            //??????-????????????-???????????????-????????????
            BigDecimal realAmount = orderItem.getProductPrice()
                    .subtract(orderItem.getPromotionAmount())
                    .subtract(orderItem.getCouponAmount())
                    .subtract(orderItem.getIntegrationAmount());
            orderItem.setRealAmount(realAmount);
        }
    }
    /**
     * ????????????????????????
     */
    private BigDecimal calcPayAmount(OmsOrder order) {
        //?????????+??????-????????????-???????????????-????????????
        BigDecimal payAmount = order.getTotalAmount()
                .add(order.getFreightAmount())
                .subtract(order.getPromotionAmount())
                .subtract(order.getCouponAmount())
                .subtract(order.getIntegrationAmount());
        return payAmount;
    }

    /**
     * ???????????????????????????
     */
    private BigDecimal calcIntegrationAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal integrationAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getIntegrationAmount() != null) {
                integrationAmount = integrationAmount.add(orderItem.getIntegrationAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return integrationAmount;
    }

    /**
     * ???????????????????????????
     */
    private BigDecimal calcCouponAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal couponAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getCouponAmount() != null) {
                couponAmount = couponAmount.add(orderItem.getCouponAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return couponAmount;
    }

    /**
     * ????????????????????????
     */
    private BigDecimal calcPromotionAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal promotionAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getPromotionAmount() != null) {
                promotionAmount = promotionAmount.add(orderItem.getPromotionAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return promotionAmount;
    }
    /**
     * ??????????????????????????????
     *
     * @param useIntegration ?????????????????????
     * @param totalAmount    ???????????????
     * @param currentMember  ???????????????
     * @param hasCoupon      ???????????????????????????
     */
    private BigDecimal getUseIntegrationAmount(Integer useIntegration, BigDecimal totalAmount, UmsMember currentMember, boolean hasCoupon) {
        BigDecimal zeroAmount = new BigDecimal(0);
        //????????????????????????????????????
        if (useIntegration.compareTo(currentMember.getIntegration()) > 0) {
            return zeroAmount;
        }
        //??????????????????????????????????????????
        //??????????????????????????????
        UmsIntegrationConsumeSetting integrationConsumeSetting = memberFeignClient.selectIntegrationConsumeSettingById(1L);
        if (hasCoupon && integrationConsumeSetting.getCouponStatus().equals(0)) {
            //????????????????????????
            return zeroAmount;
        }
        //????????????????????????????????????
        if (useIntegration.compareTo(integrationConsumeSetting.getUseUnit()) < 0) {
            return zeroAmount;
        }
        //???????????????????????????????????????
        BigDecimal integrationAmount = new BigDecimal(useIntegration).divide(new BigDecimal(integrationConsumeSetting.getUseUnit()), 2, RoundingMode.HALF_EVEN);
        BigDecimal maxPercent = new BigDecimal(integrationConsumeSetting.getMaxPercentPerOrder()).divide(new BigDecimal(100), 2, RoundingMode.HALF_EVEN);
        if (integrationAmount.compareTo(totalAmount.multiply(maxPercent)) > 0) {
            return zeroAmount;
        }
        return integrationAmount;
    }

    /**
     * ??????????????????????????????
     *
     * @param orderItemList       order_item??????
     * @param couponHistoryDetail ?????????????????????
     */
    private void handleCouponAmount(List<OmsOrderItem> orderItemList, SmsCouponHistoryDetail couponHistoryDetail) {
        SmsCoupon coupon = couponHistoryDetail.getCoupon();
        if (coupon.getUseType().equals(0)) {
            //????????????
            calcPerCouponAmount(orderItemList, coupon);
        } else if (coupon.getUseType().equals(1)) {
            //????????????
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 0);
            calcPerCouponAmount(couponOrderItemList, coupon);
        } else if (coupon.getUseType().equals(2)) {
            //????????????
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 1);
            calcPerCouponAmount(couponOrderItemList, coupon);
        }
    }

    /**
     * ?????????????????????????????????????????????????????????
     *
     * @param orderItemList ????????????????????????????????????
     */
    private void calcPerCouponAmount(List<OmsOrderItem> orderItemList, SmsCoupon coupon) {
        BigDecimal totalAmount = calcTotalAmount(orderItemList);
        for (OmsOrderItem orderItem : orderItemList) {
            //(????????????/??????????????????)*???????????????
            BigDecimal couponAmount = orderItem.getProductPrice().divide(totalAmount, 3, RoundingMode.HALF_EVEN).multiply(coupon.getAmount());
            orderItem.setCouponAmount(couponAmount);
        }
    }
    /**
     * ??????????????????????????????????????????
     *
     * @param couponHistoryDetail ???????????????
     * @param orderItemList       ????????????
     * @param type                ?????????????????????0->???????????????1->????????????
     */
    private List<OmsOrderItem> getCouponOrderItemByRelation(SmsCouponHistoryDetail couponHistoryDetail, List<OmsOrderItem> orderItemList, int type) {
        List<OmsOrderItem> result = new ArrayList<>();
        if (type == 0) {
            List<Long> categoryIdList = new ArrayList<>();
            for (SmsCouponProductCategoryRelation productCategoryRelation : couponHistoryDetail.getCategoryRelationList()) {
                categoryIdList.add(productCategoryRelation.getProductCategoryId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (categoryIdList.contains(orderItem.getProductCategoryId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        } else if (type == 1) {
            List<Long> productIdList = new ArrayList<>();
            for (SmsCouponProductRelation productRelation : couponHistoryDetail.getProductRelationList()) {
                productIdList.add(productRelation.getProductId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (productIdList.contains(orderItem.getProductId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        }
        return result;
    }
    /**
     * ???????????????
     */
    private BigDecimal calcTotalAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal totalAmount = new BigDecimal("0");
        for (OmsOrderItem item : orderItemList) {
            totalAmount = totalAmount.add(item.getProductPrice().multiply(new BigDecimal(item.getProductQuantity())));
        }
        return totalAmount;
    }

    /**
     * ?????????????????????????????????
     */

    public void lockStockByOrder(List<OmsOrderItem> cartPromotionItemList,String type) {
        for (OmsOrderItem item : cartPromotionItemList) {
            if (item.getType().equals(AllEnum.OrderItemType.GOODS.code())) {
                if (type!=null && "6".equals(type)){
                    SmsFlashPromotionProductRelation relation = markingFeignClinent.getFlashPromotionProductRelationById(item.getGiftIntegration().longValue());
                    if ((relation.getFlashPromotionCount() - item.getProductQuantity()) < 0) {
                        throw new ApiMallPlusException("SmsFlashPromotionProductRelation is stock out. goodsId=" + item.getProductId() + ", relation=" + relation.getId());
                    }
                    relation.setFlashPromotionCount(relation.getFlashPromotionCount() - item.getProductQuantity());
                    markingFeignClinent.updateFlashPromotionProductRelationById(relation);
                }
                PmsProduct goods = pmsFeignClinent.selectById(item.getProductId());
                if (goods != null && goods.getId() != null) {
                    if (true) {
                        redisUtil.delete(String.format(RedisToolsConstant.GOODSDETAIL, goods.getId() + ""));
                        PmsProduct newGoods = new PmsProduct();
                        newGoods.setId(goods.getId());
                        if (!ValidatorUtils.empty(item.getProductSkuId()) && item.getProductSkuId() > 0) {

                            PmsSkuStock skuStock = pmsFeignClinent.selectSkuById(item.getProductSkuId());
                            if ((skuStock.getStock() - item.getProductQuantity()) < 0) {
                                throw new BusinessException("goods is stock out. goodsId=" + item.getProductId() + ", skuId=" + item.getProductSkuId());
                            } else {
                                skuStock.setId(item.getProductSkuId());
                                skuStock.setStock(skuStock.getStock() - item.getProductQuantity());
                                pmsFeignClinent.updateSkuById(skuStock);

                                newGoods.setSale(goods.getSale() + item.getProductQuantity());
                                newGoods.setStock(goods.getStock() - item.getProductQuantity());
                                pmsFeignClinent.updateGoodsById(newGoods);
                            }
                        } else {

                            if ((goods.getStock() - item.getProductQuantity()) < 0) {
                                throw new BusinessException("goods is stock out. goodsId=" + item.getProductId() + ", goodsId=" + item.getProductSkuId());
                            } else {
                                newGoods.setStock(goods.getStock() - item.getProductQuantity());
                                newGoods.setSale(goods.getSale() + item.getProductQuantity());
                                pmsFeignClinent.updateGoodsById(newGoods);
                            }
                        }
                    }
                }
            }else {
                PmsGifts goods = pmsFeignClinent.getGiftById(item.getProductId());
                if (goods != null && goods.getId() != null) {
                    PmsGifts newGoods = new PmsGifts();
                    newGoods.setId(goods.getId());
                    if ((goods.getStock() - item.getProductQuantity()) < 0) {
                        throw new ApiMallPlusException("goods is stock out. goodsId=" + item.getProductId() + ", goodsId=" + item.getProductSkuId());
                    }
                    newGoods.setStock(goods.getStock() - item.getProductQuantity());
                    pmsFeignClinent.updateGiftById(newGoods);
                }
            }
        }
    }

    @Override
    public void releaseStock(OmsOrder order){
        List<OmsOrderItem> itemList = orderItemService.list(new QueryWrapper<OmsOrderItem>().eq("order_id", order.getId()));
        if (itemList != null && itemList.size() > 0) {
            for (OmsOrderItem item : itemList) {
                if (item.getType().equals(AllEnum.OrderItemType.GOODS.code())) {
                    if ("6".equals(order.getOrderType())) {
                        SmsFlashPromotionProductRelation relation = markingFeignClinent.getFlashPromotionProductRelationById(item.getGiftIntegration().longValue());
                        relation.setFlashPromotionCount(relation.getFlashPromotionCount() + item.getProductQuantity());
                        markingFeignClinent.updateFlashPromotionProductRelationById(relation);
                    }
                    PmsProduct goods = pmsFeignClinent.selectById(item.getProductId());
                    if (goods != null && goods.getId() != null) {
                        redisUtil.delete(String.format(RedisToolsConstant.GOODSDETAIL, goods.getId() + ""));
                        goods.setStock(goods.getStock() + item.getProductQuantity());
                        goods.setSale(goods.getSale() - item.getProductQuantity());
                        pmsFeignClinent.updateGoodsById(goods);
                        if (!ValidatorUtils.empty(item.getProductSkuId()) && item.getProductSkuId() > 0) {
                            PmsSkuStock skuStock = new PmsSkuStock();
                            skuStock.setId(item.getProductSkuId());
                            skuStock.setStock(skuStock.getStock() + item.getProductQuantity());
                            skuStock.setSale(skuStock.getSale() - item.getProductQuantity());
                            pmsFeignClinent.updateSkuById(skuStock);
                        }
                    }
                } else {
                    PmsGifts goods = pmsFeignClinent.getGiftById(item.getProductId());
                    if (goods != null && goods.getId() != null) {
                        PmsGifts newGoods = new PmsGifts();
                        newGoods.setId(goods.getId());
                        if ((goods.getStock() + item.getProductQuantity()) < 0) {
                            throw new ApiMallPlusException("goods is stock out. goodsId=" + item.getProductId() + ", goodsId=" + item.getProductSkuId());
                        }
                        newGoods.setStock(goods.getStock() + item.getProductQuantity());
                        pmsFeignClinent.updateGiftById(newGoods);
                    }
                }
            }
        }
    }
    /**
     * ????????????????????????????????????
     */
    private boolean hasStock(List<CartPromotionItem> cartPromotionItemList) {
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            if (cartPromotionItem.getRealStock() <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * ?????????????????????????????????
     */
    private Integer calcGiftGrowth(List<OmsOrderItem> orderItemList) {
        Integer sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum = sum + orderItem.getGiftGrowth() * orderItem.getProductQuantity();
        }
        return sum;
    }
    /**
     * ????????????????????????????????????
     */
    private void deleteCartItemList(List<OmsCartItem> cartPromotionItemList, UmsMember currentMember) {
        List<Long> ids = new ArrayList<>();
        for (OmsCartItem cartPromotionItem : cartPromotionItemList) {
            ids.add(cartPromotionItem.getId());
        }
        cartItemService.delete(currentMember.getId(), ids);
    }
    /**
     * ??????????????????????????????
     */
    private Integer calcGifIntegration(List<OmsOrderItem> orderItemList) {
       return 0;
    }
    @Override
    public ConfirmOrderResult addGroup(OrderParam orderParam) {
        List<OmsCartItem> list = new ArrayList<>();
        if (ValidatorUtils.empty(orderParam.getTotal())) {
            orderParam.setTotal(1);
        }
        OmsCartItem cartItem = new OmsCartItem();
        PmsProduct pmsProduct = pmsFeignClinent.selectById(orderParam.getGoodsId());
        createCartObj(orderParam, list, cartItem, pmsProduct);
        ConfirmOrderResult result = new ConfirmOrderResult();
        //?????????????????????

        result.setCartPromotionItemList(list);
        //??????????????????????????????
        UmsMemberReceiveAddress queryU = new UmsMemberReceiveAddress();
        queryU.setMemberId(orderParam.getMemberId());
        List<UmsMemberReceiveAddress> memberReceiveAddressList = addressService.list(new QueryWrapper<>(queryU));
        result.setMemberReceiveAddressList(memberReceiveAddressList);
        UmsMemberReceiveAddress address = addressService.getDefaultItem(orderParam.getMemberId());

        UmsMember member = memberFeignClient.findById(orderParam.getMemberId());
        //??????????????????
        result.setMemberIntegration(member.getIntegration());
       /* //?????????????????????????????????
        List<SmsCouponHistoryDetail> couponHistoryDetailList = couponService.listCart(list, 1);
        result.setCouponHistoryDetailList(couponHistoryDetailList);
        //????????????????????????
        UmsIntegrationConsumeSetting integrationConsumeSetting = integrationConsumeSettingMapper.selectById(1L);
        result.setIntegrationConsumeSetting(integrationConsumeSetting);*/
        //?????????????????????????????????????????????
        ConfirmOrderResult.CalcAmount calcAmount = calcCartAmount(list, BigDecimal.ZERO, BigDecimal.ZERO);
        result.setCalcAmount(calcAmount);
        result.setAddress(address);

        return result;
    }


    @Override
    public CommonResult acceptGroup(OrderParam orderParam) {


        UmsMember currentMember = memberFeignClient.findById(orderParam.getMemberId());
        List<OmsCartItem> list = new ArrayList<>();
        if (ValidatorUtils.empty(orderParam.getTotal())) {
            orderParam.setTotal(1);
        }
        OmsCartItem cartItem = new OmsCartItem();
        PmsProduct pmsProduct = pmsFeignClinent.selectById(orderParam.getGoodsId());
        createCartObj(orderParam, list, cartItem, pmsProduct);


        List<OmsOrderItem> orderItemList = new ArrayList<>();
        //??????????????????????????????
        String name = "";

        for (OmsCartItem cartPromotionItem : list) {
            PmsProduct goods = pmsFeignClinent.selectById(cartPromotionItem.getProductId());
            if (!ValidatorUtils.empty(cartPromotionItem.getProductSkuId()) && cartPromotionItem.getProductSkuId() > 0) {
                checkGoods(goods, false, cartPromotionItem.getQuantity());
                PmsSkuStock skuStock = pmsFeignClinent.selectSkuById(cartPromotionItem.getProductSkuId());
                checkSkuGoods(skuStock, cartPromotionItem.getQuantity());
            } else {
                checkGoods(goods, true, cartPromotionItem.getQuantity());
            }
            //????????????????????????
            OmsOrderItem orderItem = createOrderItem(cartPromotionItem);
            orderItemList.add(orderItem);
        }

        //??????????????????
        lockStockByOrder(orderItemList,"1");
        //?????????????????????????????????????????????????????????????????????????????????
        UmsMemberReceiveAddress address = addressService.getById(orderParam.getAddressId());

        OmsOrder order =new OmsOrder();
        createOrderObj(order,orderParam, currentMember, orderItemList, address);

        order.setMemberId(orderParam.getMemberId());

        // TODO: 2018/9/3 bill_*,delivery_*
        //??????order??????order_item???
        orderService.save(order);
        orderParam.setOrderId(order.getId());
        orderParam.setMemberIcon(currentMember.getIcon());
        markingFeignClinent.acceptGroup(orderParam);
        for (OmsOrderItem orderItem : orderItemList) {
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
        }
        orderItemService.saveBatch(orderItemList);


        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItemList", orderItemList);

        String platform = orderParam.getPlatform();

        return new CommonResult().success("????????????", result);
    }

    private void createCartObj(OrderParam orderParam, List<OmsCartItem> list, OmsCartItem cartItem, PmsProduct pmsProduct) {
        if (ValidatorUtils.notEmpty(orderParam.getSkuId())) {
            PmsSkuStock pmsSkuStock = pmsFeignClinent.selectSkuById(orderParam.getSkuId());
            checkGoods(pmsProduct, false, 1);
            checkSkuGoods(pmsSkuStock, 1);
            cartItem.setProductId(pmsSkuStock.getProductId());
            cartItem.setMemberId(orderParam.getMemberId());
            cartItem.setProductSkuId(pmsSkuStock.getId());
           // cartItem.setChecked(1);
            cartItem.setPrice(pmsSkuStock.getPrice());
            cartItem.setProductSkuCode(pmsSkuStock.getSkuCode());
            cartItem.setQuantity(orderParam.getTotal());
            cartItem.setProductAttr(pmsSkuStock.getMeno());
            cartItem.setProductPic(pmsSkuStock.getPic());
            cartItem.setSp1(pmsSkuStock.getSp1());
            cartItem.setSp2(pmsSkuStock.getSp2());
            cartItem.setSp3(pmsSkuStock.getSp3());
            cartItem.setProductName(pmsSkuStock.getProductName());
            cartItem.setProductCategoryId(pmsProduct.getProductCategoryId());
            cartItem.setProductBrand(pmsProduct.getBrandName());
            cartItem.setCreateDate(new Date());

        } else {
            checkGoods(pmsProduct, true, orderParam.getTotal());
            cartItem.setProductId(orderParam.getGoodsId());
            cartItem.setMemberId(orderParam.getMemberId());
          //  cartItem.setChecked(1);
            cartItem.setPrice(pmsProduct.getPrice());
            cartItem.setProductName(pmsProduct.getName());
            cartItem.setQuantity(orderParam.getTotal());
            cartItem.setProductPic(pmsProduct.getPic());
            cartItem.setCreateDate(new Date());
            cartItem.setMemberId(orderParam.getMemberId());
            cartItem.setProductCategoryId(pmsProduct.getProductCategoryId());
            cartItem.setProductBrand(pmsProduct.getBrandName());

        }
        list.add(cartItem);
    }

    private OmsOrderItem createOrderItem(OmsCartItem cartPromotionItem) {
        OmsOrderItem orderItem = new OmsOrderItem();
        orderItem.setProductAttr(cartPromotionItem.getProductAttr());
        orderItem.setProductId(cartPromotionItem.getProductId());
        orderItem.setProductName(cartPromotionItem.getProductName());
        orderItem.setProductPic(cartPromotionItem.getProductPic());
        orderItem.setProductAttr(cartPromotionItem.getProductAttr());
        orderItem.setProductBrand(cartPromotionItem.getProductBrand());
        orderItem.setProductSn(cartPromotionItem.getProductSn());
        orderItem.setProductPrice(cartPromotionItem.getPrice());
        orderItem.setProductQuantity(cartPromotionItem.getQuantity());
        orderItem.setProductSkuId(cartPromotionItem.getProductSkuId());
        orderItem.setProductSkuCode(cartPromotionItem.getProductSkuCode());
        orderItem.setProductCategoryId(cartPromotionItem.getProductCategoryId());
           /* orderItem.setPromotionAmount(cartPromotionItem.getReduceAmount());
            orderItem.setPromotionName(cartPromotionItem.getPromotionMessage());
            orderItem.setGiftIntegration(cartPromotionItem.getIntegration());
            orderItem.setGiftGrowth(cartPromotionItem.getGrowth());*/
        return orderItem;
    }

    private OmsOrder createOrderObj(OmsOrder order, OrderParam orderParam, UmsMember currentMember, List<OmsOrderItem> orderItemList, UmsMemberReceiveAddress address) {
        order.setIsComment(1);
        order.setTaxType(1);
        order.setDiscountAmount(new BigDecimal(0));
        order.setTotalAmount(calcTotalAmount(orderItemList));

        if (ValidatorUtils.notEmpty(orderParam.getGroupId())) {
            order.setGroupId(orderParam.getGroupId());
        }
        if (orderParam.getUseIntegration() == null) {
            order.setIntegration(0);
            order.setIntegrationAmount(new BigDecimal(0));
        } else {
            order.setIntegration(orderParam.getUseIntegration());
            order.setIntegrationAmount(calcIntegrationAmount(orderItemList));
        }
        //???????????????????????????????????????
        order.setCreateTime(new Date());
        order.setMemberUsername(currentMember.getUsername());
        order.setMemberId(currentMember.getId());
        //???????????????0->????????????1->????????????2->??????
        order.setPayType(orderParam.getPayType());
        //???????????????0->PC?????????5->app?????? 2 h5 3??????????????? 4 ??????????????????
        order.setSourceType(orderParam.getSource());
        //??????????????????????????????1->????????????2->????????????3->????????????4->????????????5->???????????? 6->????????????
        order.setStatus(OrderStatus.INIT.getValue());
        //???????????????0->???????????????1->????????????
        order.setOrderType(orderParam.getOrderType());
        //???????????????????????????????????????????????????
        if (address != null) {
            order.setReceiverId(address.getId());
            order.setReceiverName(address.getName());
            order.setReceiverPhone(address.getPhoneNumber());
            order.setReceiverPostCode(address.getPostCode());
            order.setReceiverProvince(address.getProvince());
            order.setReceiverCity(address.getCity());
            order.setReceiverRegion(address.getRegion());
            order.setReceiverDetailAddress(address.getDetailAddress());
        }
        //0->????????????1->?????????
        order.setConfirmStatus(0);
        order.setDeleteStatus(0);
        order.setMemberId(currentMember.getId());
        //???????????????
        order.setOrderSn(generateOrderSn(order));
        return order;
    }
    @Override
    @Transactional
    public boolean closeOrder(OmsOrder order){
        releaseStock(order);
        order.setStatus(OrderStatus.CLOSED.getValue());
        return orderMapper.updateById(order) > 0;
    }


    @Override
    @Transactional
    public  Object jifenPay(OrderParam orderParam){
        UmsMember member= memberFeignClient.findById(orderParam.getMemberId());
        PmsGifts gifts = pmsFeignClinent.getGiftById(orderParam.getGoodsId());
        if(gifts.getPrice().intValue()>member.getIntegration()){
            return new CommonResult().failed("???????????????");
        }else {
            // UmsMemberReceiveAddress address = addressService.getById(orderParam.getAddressId());

            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductId(orderParam.getGoodsId());
            orderItem.setProductName(gifts.getTitle());
            orderItem.setProductPic(gifts.getIcon());
            orderItem.setProductPrice(gifts.getPrice());
            orderItem.setProductQuantity(1);
            orderItem.setProductCategoryId(gifts.getCategoryId());
            List<OmsOrderItem> omsOrderItemList = new ArrayList<>();
            omsOrderItemList.add(orderItem);
            OmsOrder order = new OmsOrder();
                    createOrderObj(order,orderParam, member, omsOrderItemList, null);
            order.setOrderType(2);
            order.setStatus(OrderStatus.TO_DELIVER.getValue());
            order.setPayType(3);
            orderService.save(order);
            orderItem.setOrderId(order.getId());
            orderItemService.save(orderItem);
            member.setIntegration(member.getIntegration()-gifts.getPrice().intValue());
            memberFeignClient.updateMember(member);

        }
        return new CommonResult().success("????????????");
    }
    @Override
    public Object addCart(CartParam cartParam) {
        if (ValidatorUtils.empty(cartParam.getTotal())) {
            cartParam.setTotal(1);
        }
        UmsMember umsMember = memberFeignClient.findById(cartParam.getMemberId());
        OmsCartItem cartItem = new OmsCartItem();
        PmsProduct pmsProduct = pmsFeignClinent.selectById(cartParam.getGoodsId());
        if (ValidatorUtils.notEmpty(cartParam.getSkuId())){

            PmsSkuStock pmsSkuStock = pmsFeignClinent.selectSkuById(cartParam.getSkuId());
            checkGoods(pmsProduct, false, cartParam.getTotal());
            checkSkuGoods(pmsSkuStock, cartParam.getTotal());
            cartItem.setProductId(pmsSkuStock.getProductId());
            cartItem.setMemberId(cartParam.getMemberId());
            cartItem.setProductSkuId(pmsSkuStock.getId());
            OmsCartItem existCartItem   = cartItemMapper.selectOne(new QueryWrapper<>(cartItem));
            if (existCartItem == null) {
                cartItem.setPrice(pmsSkuStock.getPrice());
                cartItem.setProductSkuCode(pmsSkuStock.getSkuCode());
                cartItem.setQuantity(cartParam.getTotal());
                cartItem.setProductAttr(pmsSkuStock.getMeno1());
                cartItem.setProductPic(pmsSkuStock.getPic());
                cartItem.setSp1(pmsSkuStock.getSp1());
                cartItem.setSp2(pmsSkuStock.getSp2());
                cartItem.setSp3(pmsSkuStock.getSp3());
                cartItem.setProductName(pmsSkuStock.getProductName());
                cartItem.setCreateDate(new Date());
                cartItemMapper.insert(cartItem);
            } else {
                cartItem.setModifyDate(new Date());
                existCartItem.setQuantity(existCartItem.getQuantity() + cartParam.getTotal());
                cartItemMapper.updateById(existCartItem);
                return new CommonResult().success(existCartItem);
            }
        }else {

            checkGoods(pmsProduct, true, cartParam.getTotal());
            cartItem.setProductId(pmsProduct.getId());
            cartItem.setMemberId(cartItem.getMemberId());
            OmsCartItem existCartItem   = cartItemMapper.selectOne(new QueryWrapper<>(cartItem));
            if (existCartItem == null) {
                cartItem.setPrice(pmsProduct.getPrice());
                cartItem.setProductName(pmsProduct.getName());
                cartItem.setQuantity(cartParam.getTotal());
                cartItem.setProductPic(pmsProduct.getPic());
                cartItem.setCreateDate(new Date());
                cartItemMapper.insert(cartItem);
            } else {
                cartItem.setModifyDate(new Date());
                existCartItem.setQuantity(existCartItem.getQuantity() + cartParam.getTotal());
                cartItemMapper.updateById(existCartItem);
                return new CommonResult().success(existCartItem);
            }
        }


        return new CommonResult().success(cartItem);
    }
    private void checkGoods(PmsProduct goods, boolean falg, int count) {
        if (goods == null || goods.getId() == null) {
            throw new ApiMallPlusException("???????????????");
        }
        if (falg && (goods.getStock() <= 0 || goods.getStock() < count)) {
            throw new ApiMallPlusException("????????????!");
        }
    }

    private void checkSkuGoods(PmsSkuStock goods, int count) {
        if (goods == null || goods.getId() == null) {
            throw new ApiMallPlusException("???????????????");
        }
        if (goods.getStock() <= 0 || goods.getStock() < count) {
            throw new ApiMallPlusException("????????????!");
        }
    }
    /**
     * ??????18???????????????:8?????????+2???????????????+2???????????????+6???????????????id
     */
    private String generateOrderSn(OmsOrder order) {
        StringBuilder sb = new StringBuilder();
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        sb.append(date);
        sb.append(String.format("%02d", order.getSourceType()));
        sb.append(String.format("%02d", order.getPayType()));
        sb.append(order.getMemberId());
        return sb.toString();
    }
    /**
     * ????????????
     */
    public void push(GroupAndOrderVo umsMember, OmsOrder order, String page, String formId) {
        log.info("?????????????????????userId=" + umsMember.getMemberId() + ",orderId=" + order.getId() + ",formId=" + formId);
        if (StringUtils.isEmpty(formId)) {
            log.error("?????????????????????userId=" + umsMember.getMemberId() + ",orderId=" + order.getId() + ",formId=" + formId);
        }
        String accessToken = null;
        try {
            accessToken = wechatApiService.getAccessToken();

            String templateId = wxAppletProperties.getTemplateId();
            Map<String, TemplateData> param = new HashMap<String, TemplateData>();
            param.put("keyword1", new TemplateData(DateUtils.format(order.getCreateTime(), "yyyy-MM-dd"), "#EE0000"));

            param.put("keyword2", new TemplateData(order.getGoodsName(), "#EE0000"));
            param.put("keyword3", new TemplateData(order.getOrderSn(), "#EE0000"));
            param.put("keyword3", new TemplateData(order.getPayAmount() + "", "#EE0000"));

            JSON jsonObject = JSONUtil.parseObj(param);
            //??????????????????????????????????????????    ********???????????????????????????????????????????????????ID
            WX_TemplateMsgUtil.sendWechatMsgToUser(umsMember.getWxid(), templateId, page + "?id=" + order.getId(),
                    formId, jsonObject, accessToken);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * ????????????
     * @return
     */
    @Override
    public ConfirmOrderResult submitPreview(OrderParam orderParam) {
        if (ValidatorUtils.empty(orderParam.getTotal())) {
            orderParam.setTotal(1);
        }
        String type = orderParam.getType();
        StopWatch stopWatch = new StopWatch("??????orderType=" + orderParam.getOrderType());
        stopWatch.start("1. ?????????????????????");
        UmsMember currentMember = memberFeignClient.findById(orderParam.getMemberId());
        List<OmsCartItem> list = new ArrayList<>();
        if ("3".equals(type)) { // 1 ???????????? 2 ??????????????? 3????????????????????????
            list = cartItemService.list(currentMember.getId(), null);
        }else if ("1".equals(type)) {
            String cartId = orderParam.getCartId();
            if (org.apache.commons.lang.StringUtils.isBlank(cartId)) {
                throw new ApiMallPlusException("????????????");
            }
            OmsCartItem omsCartItem = cartItemService.selectById(Long.valueOf(cartId));
            if (omsCartItem == null) {
                return null;
            }
            list.add(omsCartItem);
        } else if ("2".equals(type)) {
            String cart_id_list1 = orderParam.getCartIds();
            if (org.apache.commons.lang.StringUtils.isBlank(cart_id_list1)) {
                throw new ApiMallPlusException("????????????");
            }
            String[] ids1 = cart_id_list1.split(",");
            List<Long> resultList = new ArrayList<>(ids1.length);
            for (String s : ids1) {
                resultList.add(Long.valueOf(s));
            }
            list = cartItemService.list(currentMember.getId(), resultList);
        }else if ("6".equals(type)) { // ??????
            SmsFlashPromotionProductRelation relation = markingFeignClinent.getFlashPromotionProductRelationById(orderParam.getSkillId());
            PmsProduct product = pmsFeignClinent.selectById(relation.getProductId());
            OmsCartItem omsCartItem = new OmsCartItem();
            omsCartItem.setQuantity(orderParam.getTotal());
            if (orderParam.getTotal()>relation.getFlashPromotionLimit()){
                throw new ApiMallPlusException("?????????????????????");
            }
            omsCartItem.setPrice(relation.getFlashPromotionPrice());
            omsCartItem.setProductBrand(product.getBrandId()+"");
            omsCartItem.setProductCategoryId(product.getProductCategoryId());
            omsCartItem.setProductName(product.getName());
            omsCartItem.setProductPic(product.getPic());
            omsCartItem.setProductId(product.getId());
            omsCartItem.setProductSn(product.getProductSn());

            list.add(omsCartItem);
        }
        if (list == null && list.size() < 1) {
            throw new ApiMallPlusException("???????????????");
        }
        List<OmsCartItem> newCartList = new ArrayList<>();
        // ?????????????????????
        BigDecimal transFee = BigDecimal.ZERO;
        for (OmsCartItem cart : list) {
            PmsProduct goods = pmsFeignClinent.selectById(cart.getProductId());
            if (goods != null && goods.getStock() > 0 && goods.getStock() >= cart.getQuantity()) {
                if (goods.getTransfee().compareTo(transFee) > 0) {
                    transFee = goods.getTransfee();
                }
            }
            newCartList.add(cart);
        }
        stopWatch.stop();
        stopWatch.start("??????????????????");
        ConfirmOrderResult result = new ConfirmOrderResult();
        //?????????????????????
        CartMarkingVo vo = new CartMarkingVo();
        vo.setCartList(newCartList);
        //????????????
        int firstOrder = orderMapper.selectCount(new QueryWrapper<OmsOrder>().eq("member_id", currentMember.getId()));
        vo.setType(1);
        if (firstOrder > 0) {
            vo.setType(2);
        }

        List<SmsBasicGifts> basicGiftsList = markingFeignClinent.matchOrderBasicGifts(vo);
        log.info(com.alibaba.fastjson.JSONObject.toJSONString(basicGiftsList));
        result.setBasicGiftsList(basicGiftsList);
        stopWatch.stop();
        stopWatch.start("??????????????????");
        result.setCartPromotionItemList(newCartList);
        //??????????????????????????????
        UmsMemberReceiveAddress queryU = new UmsMemberReceiveAddress();
        queryU.setMemberId(currentMember.getId());
        List<UmsMemberReceiveAddress> memberReceiveAddressList = addressService.list(new QueryWrapper<>(queryU));
        result.setMemberReceiveAddressList(memberReceiveAddressList);
        UmsMemberReceiveAddress address = addressService.getDefaultItem(currentMember.getId());
        //?????????????????????????????????
        vo.setMemberId(currentMember.getId());
        List<SmsCouponHistoryDetail> couponHistoryDetailList = markingFeignClinent.listCart(vo);
        result.setCouponHistoryDetailList(couponHistoryDetailList);
        //??????????????????
        result.setMemberIntegration(currentMember.getIntegration());
        //????????????????????????
        UmsIntegrationConsumeSetting integrationConsumeSetting = memberFeignClient.selectIntegrationConsumeSettingById(1L);
        result.setIntegrationConsumeSetting(integrationConsumeSetting);
        //?????????????????????????????????????????????
        if (list != null && list.size() > 0) {
            ConfirmOrderResult.CalcAmount calcAmount = calcCartAmount(newCartList, transFee, BigDecimal.ZERO);
            result.setCalcAmount(calcAmount);
            result.setAddress(address);
            return result;
        }
        stopWatch.stop();
        log.info(stopWatch.prettyPrint());
        return null;

    }
    /**
     * ??????????????????????????????
     */
    @Override
    public CommonResult generateOrder(OrderParam orderParam) {
        if (ValidatorUtils.empty(orderParam.getTotal())) {
            orderParam.setTotal(1);
        }
        UmsMember member= memberFeignClient.findById(orderParam.getMemberId());
        String type = orderParam.getType();
        StopWatch stopWatch = new StopWatch("??????orderType=" + orderParam.getOrderType());
        stopWatch.start("1. ?????????????????????");
        List<OmsCartItem> cartPromotionItemList = new ArrayList<>();
        OmsOrder order = new OmsOrder();
        //????????????
        if (orderParam.getOrderType() == 3) {
            SmsGroupActivity smsGroupActivity = markingFeignClinent.getSmsGroupActivityById(orderParam.getGroupActivityId());
            if (ValidatorUtils.notEmpty(smsGroupActivity.getGoodsIds())) {
                List<PmsProduct> productList = (List<PmsProduct>) pmsFeignClinent.listGoodsByIds(
                        Arrays.asList(smsGroupActivity.getGoodsIds().split(",")).stream().map(s -> Long.parseLong(s.trim())).collect(Collectors.toList()));
                if (productList != null && productList.size() > 0) {
                    order.setFreightAmount(smsGroupActivity.getTransfee());
                    //?????????????????????
                    cartPromotionItemList = goodsToCartList(productList);
                }
            }
        } else {
            if ("3".equals(type)) { // 1 ???????????? 2 ??????????????? 3????????????????????????
                cartPromotionItemList = cartItemService.list(orderParam.getMemberId(), null);
            }
            if ("1".equals(type)) {
                Long cartId = Long.valueOf(orderParam.getCartId());
                OmsCartItem omsCartItem = cartItemService.selectById(cartId);
                if (omsCartItem != null) {
                    cartPromotionItemList.add(omsCartItem);
                } else {
                    throw new ApiMallPlusException("???????????????");
                }

            } else if ("2".equals(type)) {
                String cart_id_list1 = orderParam.getCartIds();
                String[] ids1 = cart_id_list1.split(",");
                List<Long> resultList = new ArrayList<>(ids1.length);
                for (String s : ids1) {
                    resultList.add(Long.valueOf(s));
                }
                cartPromotionItemList = cartItemService.list(orderParam.getMemberId(), resultList);
            }else if ("6".equals(type)) { // ??????
                SmsFlashPromotionProductRelation relation = markingFeignClinent.getFlashPromotionProductRelationById(orderParam.getSkillId());
                PmsProduct product = pmsFeignClinent.selectById(relation.getProductId());
                OmsCartItem omsCartItem = new OmsCartItem();
                omsCartItem.setQuantity(orderParam.getTotal());
                if (orderParam.getTotal()>relation.getFlashPromotionLimit()){
                    throw new ApiMallPlusException("?????????????????????");
                }
                omsCartItem.setPrice(relation.getFlashPromotionPrice());
                omsCartItem.setProductBrand(product.getBrandId()+"");
                omsCartItem.setProductCategoryId(product.getProductCategoryId());
                omsCartItem.setProductName(product.getName());
                omsCartItem.setProductPic(product.getPic());
                omsCartItem.setProductId(product.getId());
                omsCartItem.setProductSn(product.getProductSn());

                cartPromotionItemList.add(omsCartItem);
            }
        }
        if (cartPromotionItemList == null || cartPromotionItemList.size() < 1) {
            return new CommonResult().failed("?????????????????????");
        }
        stopWatch.stop();
        List<OmsOrderItem> orderItemList = new ArrayList<>();
        //??????????????????????????????
        String name = "";
        BigDecimal transFee = BigDecimal.ZERO;
        List<OmsCartItem> newCartItemList = new ArrayList<>();
        Integer isFirst = 1;
        stopWatch.start("2. ?????????????????????????????????????????????????????? ????????????");
        //?????????????????????????????????????????????????????????????????????????????????

        for (OmsCartItem cartPromotionItem : cartPromotionItemList) {
            boolean flag = false;
            PmsProduct goods = pmsFeignClinent.selectById(cartPromotionItem.getProductId());
            if (!ValidatorUtils.empty(cartPromotionItem.getProductSkuId()) && cartPromotionItem.getProductSkuId() > 0) {
                checkGoods(goods, false, cartPromotionItem.getQuantity());
                PmsSkuStock skuStock = pmsFeignClinent.selectSkuById(cartPromotionItem.getProductSkuId());
                if (skuStock.getStock() > 0 && skuStock.getStock() >= cartPromotionItem.getQuantity()) {
                    flag = true;
                }
            } else {
                if (goods != null && goods.getId() != null && goods.getStock() > 0 && goods.getStock() >= cartPromotionItem.getQuantity()) {
                    flag = true;
                }
            }
            if (flag) {
                if (goods.getTransfee().compareTo(transFee) > 0) {
                    transFee = goods.getTransfee();
                }
                //????????????????????????
                OmsOrderItem orderItem = createOrderItem(cartPromotionItem);
                orderItem.setType(AllEnum.OrderItemType.GOODS.code());
                orderItemList.add(orderItem);
                if (isFirst==1){
                    name = cartPromotionItem.getProductName();
                    order.setGoodsId(cartPromotionItem.getProductId());
                    order.setGoodsName(cartPromotionItem.getProductName());
                }
                newCartItemList.add(cartPromotionItem);
            }
        }


        //3.???????????????
        SmsCouponHistory couponHistory = null;
        SmsCoupon coupon = null;
        if (orderParam.getCouponId() != null) {
            couponHistory = markingFeignClinent.getcouponHistoryById(orderParam.getMemberCouponId());
            coupon = markingFeignClinent.getCouponById(orderParam.getCouponId());
        }
        //????????????????????????
        if (orderParam.getUseIntegration() == null) {
            //???????????????
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setIntegrationAmount(new BigDecimal(0));
            }
        } else {
            //????????????
            BigDecimal totalAmount = calcTotalAmount(orderItemList);
            BigDecimal integrationAmount = getUseIntegrationAmount(orderParam.getUseIntegration(), totalAmount, member, orderParam.getCouponId() != null);
            if (integrationAmount.compareTo(new BigDecimal(0)) == 0) {
                return new CommonResult().failed("???????????????");
            } else {
                //???????????????????????????????????????
                for (OmsOrderItem orderItem : orderItemList) {
                    BigDecimal perAmount = orderItem.getProductPrice().divide(totalAmount, 3, RoundingMode.HALF_EVEN).multiply(integrationAmount);
                    orderItem.setIntegrationAmount(perAmount);
                }
            }
        }

        //?????????????????????????????????????????????????????????????????????????????????
        UmsMemberReceiveAddress address = addressService.getById(orderParam.getAddressId());
        createOrderObj(order, orderParam, member, orderItemList, address);
        if (orderParam.getOrderType() != 3) {
            order.setFreightAmount(transFee);
        }
        if (orderParam.getCouponId() == null) {
            order.setCouponAmount(new BigDecimal(0));
        } else {
            order.setCouponId(orderParam.getCouponId());
            order.setCouponAmount(coupon.getAmount());
        }

        order.setPayAmount(calcPayAmount(order));
        if (order.getPayAmount().compareTo(BigDecimal.ZERO)<0){
            order.setPayAmount(new BigDecimal("0.01"));
        }
        stopWatch.stop();

        stopWatch.start("3.??????????????????");


        stopWatch.stop();
        stopWatch.start("4.??????????????? ????????????");
        //??????order??????order_item???
        orderService.save(order);
        for (OmsOrderItem orderItem : orderItemList) {
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
        }
        orderItemService.saveBatch(orderItemList);
        //?????????????????????????????????????????????
        if (orderParam.getCouponId() != null) {
            updateCouponStatus(orderParam.getCouponId(), member.getId(), 1);
        }
        //?????????????????????????????????
        if (orderParam.getUseIntegration() != null) {
            order.setUseIntegration(orderParam.getUseIntegration());
         //   memberService.updateIntegration(currentMember.getId(), currentMember.getIntegration() - orderParam.getUseIntegration());
        }
        //?????????????????????????????????
        deleteCartItemList(cartPromotionItemList, member);
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItemList", orderItemList);

        String platform = orderParam.getPlatform();
        if ("1".equals(platform)) {
        //    push(currentMember, order, orderParam.getPage(), orderParam.getFormId(), name);
        }
        return new CommonResult().success("????????????", result);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object confimDelivery(Long id) {
        OmsOrder order = this.orderMapper.selectById(id);
        if (order.getStatus() != OrderStatus.DELIVERED.getValue()) {
            return new CommonResult().paramFailed("?????????????????????????????????");
        }
        OmsOrderOperateHistory history = updateOrderInfo(id, order, OrderStatus.TO_COMMENT);
        history.setOrderStatus(OrderStatus.TO_COMMENT.getValue());
        history.setNote("????????????");
        orderOperateHistoryService.save(history);

        return new CommonResult().success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object applyRefund(Long id){
        OmsOrder order = this.orderMapper.selectById(id);
        try {
            if (order.getStatus() >9) {
                return new CommonResult().paramFailed("????????????????????????????????????");
            }
            OmsOrderOperateHistory history = updateOrderInfo(id, order, OrderStatus.REFUNDING);
            history.setOrderStatus(OrderStatus.REFUNDING.getValue());
            history.setNote("????????????");
            orderOperateHistoryService.save(history);
        }catch (Exception e){
            e.printStackTrace();
        }

        return new CommonResult().success();
    }

    private OmsOrderOperateHistory updateOrderInfo(Long id, OmsOrder order, OrderStatus refunding) {
        String key = RedisToolsConstant.orderDetail + apiContext.getCurrentProviderId() + "orderid" + id;
        redisUtil.delete(key);
        order.setStatus(refunding.getValue());
        orderMapper.updateById(order);

        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(order.getId());
        history.setCreateTime(new Date());
        history.setOperateMan("shop");
        history.setPreStatus(order.getStatus());
        return  history;
    }

    @Override
    public Object orderComment(Long orderId, String items) {
        UmsMember member= memberFeignClient.findById(orderMapper.selectById(orderId).getMemberId());
        List<ProductConsultParam> itemss = null;
        try {
            itemss = JsonUtil.jsonToList(items, ProductConsultParam.class);
            for (ProductConsultParam param : itemss){
                PmsProductConsult productConsult = new PmsProductConsult();
                if (member!=null){
                    productConsult.setPic(member.getIcon());
                    productConsult.setMemberName(member.getNickname());
                    productConsult.setMemberId(member.getId());
                }else {
                    return new CommonResult().failed("????????????");
                }
                productConsult.setGoodsId(param.getGoodsId());
                productConsult.setOrderId(orderId);
                productConsult.setConsultContent(param.getTextarea());
                productConsult.setStars(param.getScore());
                productConsult.setEmail(Arrays.toString(param.getImages()));
                productConsult.setConsultAddtime(new Date());
                productConsult.setType(AllEnum.ConsultType.ORDER.code());
                if (ValidatorUtils.empty(param.getTextarea()) && ValidatorUtils.empty(param.getImages())){

                }else {
                    pmsFeignClinent.saveProductConsult(productConsult);
                }

            }
            OmsOrder omsOrder = new OmsOrder();
            omsOrder.setId(orderId);

            omsOrder.setStatus(OrderStatus.TRADE_SUCCESS.getValue());
            if (orderService.updateById(omsOrder)) {
                OmsOrderOperateHistory history = new OmsOrderOperateHistory();
                history.setOrderId(omsOrder.getId());
                history.setCreateTime(new Date());
                history.setOperateMan("shop");
                history.setPreStatus(OrderStatus.TO_COMMENT.getValue());
                history.setOrderStatus(OrderStatus.TRADE_SUCCESS.getValue());
                history.setNote("????????????");
                orderOperateHistoryService.save(history);
                return new CommonResult().success(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return  new CommonResult().failed();
    }

    @Transactional
    @Override
    public OmsOrder blancePay(OmsOrder order) {
        UmsMember userDO= memberFeignClient.findById(order.getMemberId());
        order.setStatus(OrderStatus.TO_DELIVER.getValue());
        order.setPayType(AllEnum.OrderPayType.balancePay.code());
        order.setPaymentTime(new Date());
        orderService.updateById(order);
        if (ValidatorUtils.notEmpty(order.getGroupId())) {
            SmsGroup group = new SmsGroup();
            group.setId(order.getGroupId());
            group.setPeoples(group.getPeoples() - 1);
            markingFeignClinent.updateGroupById(group);
        }
        if (order.getPayAmount().compareTo(BigDecimal.ZERO)<0){
            order.setPayAmount(new BigDecimal("0.01"));
        }
        userDO.setBlance(userDO.getBlance().subtract(order.getPayAmount()));
        memberFeignClient.updateMember(userDO);
        UmsMemberBlanceLog blog = new UmsMemberBlanceLog();
        blog.setMemberId(userDO.getId());
        blog.setCreateTime(new Date());
        blog.setNote("???????????????" + order.getId());
        blog.setPrice(order.getPayAmount());
        blog.setType(1);
        memberFeignClient.saveBlanceLog(blog);
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(order.getId());
        history.setCreateTime(new Date());
        history.setOperateMan("shop");
        history.setPreStatus(OrderStatus.INIT.getValue());
        history.setOrderStatus(OrderStatus.TO_DELIVER.getValue());
        history.setNote("????????????");
        orderOperateHistoryService.save(history);

        return order;
    }
    @Override
    public Object preGroupActivityOrder(OrderParam orderParam) {
        SmsGroupActivity smsGroupActivity = markingFeignClinent.getSmsGroupActivityById(orderParam.getGroupId());
        if (ValidatorUtils.notEmpty(smsGroupActivity.getGoodsIds())) {
            List<PmsProduct> productList = (List<PmsProduct>) pmsFeignClinent.listGoodsByIds(
                    Arrays.asList(smsGroupActivity.getGoodsIds().split(",")).stream().map(s -> Long.parseLong(s.trim())).collect(Collectors.toList()));
            if (productList != null && productList.size() > 0) {
                UmsMember currentMember = memberFeignClient.findById(orderParam.getMemberId());
                ConfirmOrderResult result = new ConfirmOrderResult();
                // ??????
                BigDecimal transFee = BigDecimal.ZERO;
                //?????????????????????
                List<OmsCartItem> list = new ArrayList<>();
                for (PmsProduct product : productList) {
                    if (product == null && product.getId() == null) {
                        return new CommonResult().failed("????????????");
                    }
                    if (product != null && product.getStock() < 1) {
                        return new CommonResult().failed("????????????");
                    }
                    OmsCartItem omsCartItem = new OmsCartItem();
                    omsCartItem.setProductId(product.getId());
                    omsCartItem.setPrice(product.getPrice());
                    omsCartItem.setCreateDate(new Date());
                    omsCartItem.setProductBrand(product.getBrandName());
                    omsCartItem.setProductCategoryId(product.getProductCategoryId());
                    omsCartItem.setProductName(product.getName());
                    omsCartItem.setProductSn(product.getProductSn());
                    omsCartItem.setQuantity(1);
                    omsCartItem.setProductPic(product.getPic());
                    list.add(omsCartItem);
                }
                result.setCartPromotionItemList(list);
                //??????????????????????????????
                UmsMemberReceiveAddress queryU = new UmsMemberReceiveAddress();
                queryU.setMemberId(currentMember.getId());
                List<UmsMemberReceiveAddress> memberReceiveAddressList = addressService.list(new QueryWrapper<>(queryU));
                result.setMemberReceiveAddressList(memberReceiveAddressList);
                UmsMemberReceiveAddress address = addressService.getDefaultItem(currentMember.getId());
                //?????????????????????????????????
                CartMarkingVo vo = new CartMarkingVo();
                vo.setCartList(list);
                vo.setType(1);
                vo.setMemberId(currentMember.getId());
                List<SmsCouponHistoryDetail> couponHistoryDetailList = markingFeignClinent.listCart(vo);
                result.setCouponHistoryDetailList(couponHistoryDetailList);
                //??????????????????
                result.setMemberIntegration(currentMember.getIntegration());
                //????????????????????????
                UmsIntegrationConsumeSetting integrationConsumeSetting = memberFeignClient.selectIntegrationConsumeSettingById(1L);
                result.setIntegrationConsumeSetting(integrationConsumeSetting);
                //?????????????????????????????????????????????
                if (list != null && list.size() > 0) {
                    ConfirmOrderResult.CalcAmount calcAmount = calcCartAmount(list, smsGroupActivity.getTransfee(), smsGroupActivity.getPrice());
                    result.setCalcAmount(calcAmount);
                    result.setAddress(address);
                    smsGroupActivity.setProductList(null);
                    result.setGroupActivity(smsGroupActivity);
                    return new CommonResult().success(result);
                }
                return null;
            }
        }
        return null;
    }
    private List<OmsCartItem> goodsToCartList(List<PmsProduct> productList) {
        List<OmsCartItem> cartItems = new ArrayList<>();
        for (PmsProduct product : productList) {
            OmsCartItem omsCartItem = new OmsCartItem();
            omsCartItem.setProductId(product.getId());
            omsCartItem.setPrice(product.getPrice());
            omsCartItem.setCreateDate(new Date());
            omsCartItem.setProductBrand(product.getBrandName());
            omsCartItem.setProductCategoryId(product.getProductCategoryId());
            omsCartItem.setProductName(product.getName());
            omsCartItem.setProductSn(product.getProductSn());
            omsCartItem.setQuantity(1);
            omsCartItem.setProductPic(product.getPic());
            cartItems.add(omsCartItem);
        }
        return cartItems;
    }
}
