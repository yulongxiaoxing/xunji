package cn.xunji.probe;

/** Expand a message only when the larger candidate is the same text, never another message. */
final class MessageText {
    static String expanded(String message,String expanded) {
        if(message==null || message.isEmpty()) return expanded==null?"":expanded;
        if(expanded==null || expanded.length()<=message.length()) return message;
        String prefix=message.replaceFirst("(?:…|\\.\\.\\.)+$", "").trim();
        return prefix.length()>0 && expanded.startsWith(prefix) ? expanded : message;
    }
}
