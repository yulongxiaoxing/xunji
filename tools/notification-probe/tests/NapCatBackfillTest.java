package cn.xunji.probe;
import java.util.*;
public class NapCatBackfillTest {
 static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static NapCatBackfill.Entry e(String id,long time,long order){return new NapCatBackfill.Entry(id,time,order,id);}
 static List<NapCatBackfill.Entry> list(NapCatBackfill.Entry... entries){return Arrays.asList(entries);}
 static class Feed implements NapCatBackfill.Source {
  List<NapCatBackfill.Entry> latest;Map<String,List<NapCatBackfill.Entry>> pages=new HashMap<>();int reads;
  Feed(List<NapCatBackfill.Entry> latest){this.latest=latest;}
  public List<NapCatBackfill.Entry> latest(){return latest;}
  public List<NapCatBackfill.Entry> older(String anchor){reads++;return pages.getOrDefault(anchor,Collections.emptyList());}
 }
 static NapCatBackfill.State cursor(String id,long t,long order){NapCatBackfill.State s=new NapCatBackfill.State();s.cursorId=id;s.cursorTime=t;s.cursorOrder=order;s.checkedThrough=t;return s;}
 interface Work {void run()throws Exception;}
 static void fails(Work work,String label)throws Exception{boolean failed=false;try{work.run();}catch(Exception expected){failed=true;}check(failed,label);}
 public static void main(String[] args)throws Exception {
  NapCatBackfill.Entry a=e("a",100,1),b=e("b",101,2),c=e("c",101,3),d=e("d",102,4),f=e("f",103,5);
  Feed feed=new Feed(list(a,f));feed.pages.put("f",list(c,d,f));feed.pages.put("c",list(a,b,c));
  Set<String> saved=new LinkedHashSet<>();List<NapCatBackfill.State> states=new ArrayList<>();
  NapCatBackfill.Result r=NapCatBackfill.scan(feed,x->saved.add(x.id),s->states.add(s.copy()),cursor("a",100,1),90,110,20,()->true);
  check(r.complete,"multi-page completes");check(saved.equals(new LinkedHashSet<>(Arrays.asList("c","d","f","b"))),"sparse latest list does not lose intermediate messages");
  check(r.state.cursorId.equals("f")&&r.state.checkedThrough==110,"completed checkpoint is high-water message and scan end");
  check(states.get(0).cursorId.equals("a")&&!states.get(0).resumeAnchor.isEmpty(),"partial page never advances completed cursor");
  check(saved.contains("b")&&saved.contains("c"),"same-second messages both retained");
  saved.clear();states.clear();feed.reads=0;
  NapCatBackfill.Result partial=NapCatBackfill.scan(feed,x->saved.add(x.id),s->states.add(s.copy()),cursor("a",100,1),90,110,1,()->true);
  check(!partial.complete&&partial.state.resumeAnchor.equals("c"),"page budget persists continuation");
  NapCatBackfill.Result continued=NapCatBackfill.scan(feed,x->saved.add(x.id),s->{},partial.state.copy(),90,120,1,()->true);
  check(continued.complete&&continued.state.checkedThrough==110,"restart finishes original frozen batch");check(saved.size()==4,"replayed anchor is idempotent at sink");
  Feed empty=new Feed(Collections.emptyList());fails(()->NapCatBackfill.scan(empty,x->{},s->{},cursor("a",100,1),90,110,20,()->true),"empty latest cannot advance");
  Feed stuck=new Feed(list(f));stuck.pages.put("f",list(f));fails(()->NapCatBackfill.scan(stuck,x->{},s->{},cursor("a",100,1),90,110,20,()->true),"stuck page cannot advance");
  Feed missing=new Feed(list(f));missing.pages.put("f",list(e("old",80,0),f));fails(()->NapCatBackfill.scan(missing,x->{},s->{},cursor("a",100,1),90,110,20,()->true),"missing old checkpoint is explicit gap");
  final int[] commits={0};fails(()->NapCatBackfill.scan(feed,x->{throw new Exception("disk full");},s->commits[0]++,cursor("a",100,1),90,110,20,()->true),"failed storage aborts");check(commits[0]==0,"no checkpoint commit before source save");
  fails(()->NapCatBackfill.scan(feed,x->{},s->{},cursor("a",100,1),90,110,20,()->false),"paused scan aborts");
  final boolean[] active={true};commits[0]=0;fails(()->NapCatBackfill.scan(feed,x->active[0]=false,s->commits[0]++,cursor("a",100,1),90,110,20,()->active[0]),"cancel in sink aborts checkpoint");check(commits[0]==0,"cancelled save never commits progress");
  Feed migration=new Feed(list(f));migration.pages.put("f",list(e("old",80,0),a,b,c,d,f));saved.clear();
  r=NapCatBackfill.scan(migration,x->saved.add(x.id),s->{},new NapCatBackfill.State(),100,110,20,()->true);
  check(r.complete&&saved.size()==5&&!saved.contains("old"),"legacy follow time honored inclusively");
  Feed idle=new Feed(list(a));idle.pages.put("a",list(a));r=NapCatBackfill.scan(idle,x->{throw new AssertionError("idle stored");},s->{},cursor("a",100,1),90,110,20,()->true);
  check(r.complete&&idle.reads==0,"no new message updates check time without rereading full history");
  Feed backwards=new Feed(list(a));fails(()->NapCatBackfill.scan(backwards,x->{},s->{},cursor("f",103,5),90,110,20,()->true),"regressed latest is not success");
  System.out.println(checks+" NapCat backfill pagination/cursor/failure/cancellation checks passed");
 }
}
