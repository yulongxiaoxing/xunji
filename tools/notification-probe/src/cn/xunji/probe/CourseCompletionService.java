package cn.xunji.probe;

import android.app.job.*;
import android.content.*;
import android.os.PersistableBundle;

/** Best-effort background bookkeeping. Every snapshot also reconciles against wall clock. */
public class CourseCompletionService extends JobService {
    private static final int JOB=42;
    static void schedule(Context context){
        long now=System.currentTimeMillis(),next;
        try(CardStore store=new CardStore(context)){store.completeEndedCourses(now);next=store.nextCourseEnd(now);}
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler==null)return;
        if(next==Long.MAX_VALUE){scheduler.cancel(JOB);return;}
        JobInfo pending=scheduler.getPendingJob(JOB);if(pending!=null&&pending.getExtras().getLong("end")==next)return;
        PersistableBundle extras=new PersistableBundle();extras.putLong("end",next);
        scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,CourseCompletionService.class)).setExtras(extras).setMinimumLatency(Math.max(1,next-now)).build());
    }
    @Override public boolean onStartJob(JobParameters params){
        new Thread(()->{try(CardStore store=new CardStore(this)){store.completeEndedCourses(System.currentTimeMillis());}finally{jobFinished(params,false);schedule(this);}},"xunji-course-complete").start();return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
}
