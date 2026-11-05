package cn.xunji.probe;

import java.util.*;
import java.util.function.BooleanSupplier;

/** History traversal independent of Android and QQ transport. Progress follows durable source writes. */
final class NapCatBackfill {
    static final class Entry {
        final String id; final long time,order; final Object payload;
        Entry(String id,long time,long order,Object payload){this.id=id;this.time=time;this.order=order;this.payload=payload;}
    }
    static final class State {
        String cursorId="",resumeAnchor="",headId="";
        long cursorTime,cursorOrder,checkedThrough,headTime,headOrder,batchEnd;
        State copy(){State s=new State();s.cursorId=cursorId;s.resumeAnchor=resumeAnchor;s.headId=headId;s.cursorTime=cursorTime;s.cursorOrder=cursorOrder;s.checkedThrough=checkedThrough;s.headTime=headTime;s.headOrder=headOrder;s.batchEnd=batchEnd;return s;}
    }
    interface Source {List<Entry> latest()throws Exception;List<Entry> older(String anchor)throws Exception;}
    interface Sink {void save(Entry entry)throws Exception;}
    interface Progress {void save(State state)throws Exception;}
    static final class Result {
        final State state;final boolean complete;final int visited;
        Result(State state,boolean complete,int visited){this.state=state.copy();this.complete=complete;this.visited=visited;}
    }
    private static int compare(Entry a,Entry b){int c=Long.compare(a.time,b.time);if(c==0)c=Long.compare(a.order,b.order);return c==0?a.id.compareTo(b.id):c;}
    private static void active(BooleanSupplier valid)throws Exception{if(!valid.getAsBoolean())throw new IllegalStateException("同步已暂停或关注已改变，进度保留");}
    private static List<Entry> checked(List<Entry> entries)throws Exception {
        if(entries==null||entries.isEmpty())throw new IllegalStateException("历史暂未返回，保留进度后重试");
        Map<String,Entry> unique=new LinkedHashMap<>();
        for(Entry e:entries){if(e==null||e.id==null||e.id.isEmpty()||e.time<=0)throw new IllegalStateException("历史消息资料不完整，进度未推进");unique.put(e.id,e);}
        List<Entry> rows=new ArrayList<>(unique.values());rows.sort(NapCatBackfill::compare);return rows;
    }
    static Result scan(Source source,Sink sink,Progress progress,State previous,long since,long now,int maxPages,BooleanSupplier valid)throws Exception {
        if(since<=0||now<=0||maxPages<1)throw new IllegalArgumentException("Invalid scan bounds");
        active(valid);State s=previous.copy();int visited=0;
        if(s.headId.isEmpty()){
            List<Entry> recent=checked(source.latest());active(valid);Entry head=recent.get(recent.size()-1);
            Entry cursor=new Entry(s.cursorId,s.cursorTime,s.cursorOrder,null);
            if(!s.cursorId.isEmpty()&&compare(head,cursor)<0)throw new IllegalStateException("QQ历史尚未恢复到上次位置，请稍后重试");
            if(head.time>now)throw new IllegalStateException("QQ消息时间晚于手机时间，请检查设备时间后重试");
            if(head.id.equals(s.cursorId)||s.cursorId.isEmpty()&&head.time<since){
                s.cursorId=head.id;s.cursorTime=head.time;s.cursorOrder=head.order;s.checkedThrough=now;
                active(valid);progress.save(s.copy());return new Result(s,true,0);
            }
            s.headId=head.id;s.headTime=head.time;s.headOrder=head.order;s.batchEnd=now;s.resumeAnchor=head.id;
        }
        Entry head=new Entry(s.headId,s.headTime,s.headOrder,null);
        Entry cursor=new Entry(s.cursorId,s.cursorTime,s.cursorOrder,null);
        for(int page=0;page<maxPages;page++){
            active(valid);String anchor=s.resumeAnchor;List<Entry> rows=checked(source.older(anchor));active(valid);
            boolean containsAnchor=false,reached=false;
            for(Entry row:rows){if(row.id.equals(anchor))containsAnchor=true;if(row.id.equals(s.cursorId))reached=true;}
            if(!containsAnchor)throw new IllegalStateException("历史翻页位置缺失，保留进度等待重试");
            Entry first=rows.get(0);
            if(s.cursorId.isEmpty())reached=first.time<since;
            if(!reached&&!s.cursorId.isEmpty()&&first.time<s.cursorTime)throw new IllegalStateException("历史未接上上次位置，可能有缺口；进度已保留");
            if(!reached&&first.id.equals(anchor))throw new IllegalStateException("历史未继续向前，保留进度后重试");
            for(Entry row:rows){
                if(row.time<since||row.time>s.batchEnd||compare(row,head)>0||!s.cursorId.isEmpty()&&compare(row,cursor)<=0)continue;
                active(valid);sink.save(row);visited++;
            }
            active(valid);
            if(reached){
                s.cursorId=s.headId;s.cursorTime=s.headTime;s.cursorOrder=s.headOrder;s.checkedThrough=s.batchEnd;
                s.headId="";s.resumeAnchor="";s.headTime=0;s.headOrder=0;s.batchEnd=0;
                progress.save(s.copy());return new Result(s,true,visited);
            }
            s.resumeAnchor=first.id;progress.save(s.copy());
        }
        return new Result(s,false,visited);
    }
}
