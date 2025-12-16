package com.medical.assistant.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class XunfeiResponse {

    private String action;  // started, result, error
    private String code;
    private String desc;
    private String sid;     // 会话ID
    private DataBean data;

    @Data
    public static class DataBean {
        private Integer seg_id;
        private CnBean cn;
        private Boolean ls;  // 是否为最终结果

        @Data
        public static class CnBean {
            private StBean st;

            @Data
            public static class StBean {
                private String bg;   // 句子开始时间
                private String ed;   // 句子结束时间
                private Integer type; // 0-确定性结果；1-中间结果
                private List<RtBean> rt;

                @Data
                public static class RtBean {
                    private List<WsBean> ws;

                    @Data
                    public static class WsBean {
                        private List<CwBean> cw;

                        @Data
                        public static class CwBean {
                            private String w;   // 词内容
                            private String wp;  // 词标识
                            private String wb;  // 词开始时间
                            private String we;  // 词结束时间
                            private String lg;  // 语言类型
                            private Integer rl; // 角色分离标识
                        }
                    }
                }
            }
        }
    }
}
