package cn.xunji.probe;

/** User-confirmed experiences, never inferred awards or registration counts. */
final class GrowthPolicy {
    static final java.util.List<String> CATEGORIES=java.util.Arrays.asList("竞赛","志愿","证书","项目","学生工作","校园活动","实践","其他成果");
    static void validate(String title,String category,String date,String result){
        if(title.trim().isEmpty()||title.length()>200)throw new IllegalArgumentException("请填写1至200字经历名称");
        if(!CATEGORIES.contains(category))throw new IllegalArgumentException("请选择有效的经历类别");
        if(date.matches("[0-9]{4}-[0-9]{2}")){
            try{if(date.startsWith("0000"))throw new IllegalArgumentException();java.time.YearMonth.parse(date);}catch(Exception e){throw new IllegalArgumentException("请填写有效的年月");}
        }else SchedulePolicy.validate(date,"");
        if(result.length()>5000)throw new IllegalArgumentException("结果与收获最多5000字");
    }
    static String[] fields(String category){
        switch(category){
            case "竞赛":return new String[]{"award"};
            case "志愿":return new String[]{"organizer","hours"};
            case "证书":return new String[]{"issuer","number"};
            case "项目":return new String[]{"role"};
            case "学生工作":case "实践":return new String[]{"organization"};
            case "校园活动":return new String[]{"organizer","role"};
            default:return new String[0];
        }
    }
    static void validateDetails(String category,java.util.Map<String,String> fields){
        java.util.List<String> allowed=java.util.Arrays.asList(fields(category));
        for(java.util.Map.Entry<String,String> entry:fields.entrySet()){
            if(!allowed.contains(entry.getKey()))throw new IllegalArgumentException("经历字段与类别不对应，请重新核对");
            String value=entry.getValue();if(value==null||value.length()>200)throw new IllegalArgumentException("单项补充信息最多200字");
            if("hours".equals(entry.getKey())&&!value.isEmpty()&&(!value.matches("[0-9]{1,6}(\\.[0-9]{1,2})?")||new java.math.BigDecimal(value).compareTo(new java.math.BigDecimal("100000"))>0))throw new IllegalArgumentException("服务时长请填写0至100000小时，最多两位小数");
        }
    }
    static boolean imageId(String id){return id!=null&&id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
    static boolean registration(String title){return title.contains("报名")||title.contains("报考")||title.contains("注册参赛");}
    static void requireSource(String state,String category,String title,boolean confirmed){requireSource(state,category,title,confirmed,false);}
    static void requireSource(String state,String category,String title,boolean confirmed,boolean approvedCandidate){
        if(!"done".equals(state))throw new IllegalArgumentException("来源事项尚未完成或状态已变化，请重新核对");
        if("课程".equals(category))throw new IllegalArgumentException("课程下课不会转为成长经历");
        if(registration(title)&&!approvedCandidate)throw new IllegalArgumentException("报名完成不代表实际参与；实际参与后可手动新增经历");
        if(!confirmed)throw new IllegalArgumentException("请确认已实际参与或完成这项经历");
    }
}
