package in.org.iudx.adaptor.sink;

import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import in.org.iudx.adaptor.datatypes.GenericJsonMessage;
import org.apache.flink.configuration.Configuration;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** DumbSink
 *  A dumb sink which prints the DataStream
 **/
public class DumbSink implements SinkFunction<String> {


  private static final Logger LOGGER = LogManager.getLogger(DumbSink.class);

  public DumbSink() {
  }

  /** Statefulness for dumb things */
  public void open(Configuration config) throws Exception {
  }


  /**
   * Called for every message the incoming data
   */
  @Override
  public void invoke(String value) {
    System.out.println(value);
  }

}