package in.org.iudx.adaptor.server.util;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import in.org.iudx.adaptor.server.JobScheduler;
import in.org.iudx.adaptor.server.flink.FlinkClientService;
import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;
import static in.org.iudx.adaptor.server.util.Constants.FLINK_SERVICE_ADDRESS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@PersistJobDataAfterExecution
public class FlinkJobExecute implements Job {
  FlinkClientService flinkClient;
  JsonObject request = new JsonObject();

  private static final Logger LOGGER = LogManager.getLogger(FlinkJobExecute.class);

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {

    flinkClient = JobScheduler.JobScheduler();
    
    LOGGER.debug("---------triggering FLinkJobExecute------------");
    SchedulerContext schedulerContext = null;
    try {
      schedulerContext = context.getScheduler().getContext();
    } catch (SchedulerException e) {
      // TODO Auto-generated catch block
      LOGGER.error("Error: " + e.getMessage());
    }
        
    LOGGER.debug("Scheduler context: "+ schedulerContext);
        
    final JobDataMap  jobDataMap = context.getJobDetail().getJobDataMap();
    String requestBody = (String) jobDataMap.get("data");
    //flinkClient = (FlinkClientServiceImpl) jobDataMap.get("flinkClient");
    
    LOGGER.debug("FlinkClient: "+ flinkClient);
    //String requestBody = (String) schedulerContext.get("data");
    LOGGER.debug("Data---: "+ requestBody);
    
    JsonObject data = new JsonObject(requestBody);
    
    flinkClient.handleJob(data, resHandler->{
      if (resHandler.succeeded()) {
        LOGGER.info("Success: Job submitted "+ resHandler.succeeded());
      } else {
        LOGGER.error("Error: Jar submission failed; " + resHandler.cause().getMessage());
      }
    });
  }
}
