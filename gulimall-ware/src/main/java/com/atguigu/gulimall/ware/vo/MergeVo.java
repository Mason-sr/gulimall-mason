package com.atguigu.gulimall.ware.vo;

        import lombok.Data;

        import java.util.List;

@Data
public class MergeVo {

   private Long purchaseId; //整单id，一定要提交包装类型的Long,不提交就要封装空的值
   private List<Long> items;//[1,2,3,4] //合并项集合
}
