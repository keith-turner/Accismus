/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fluo.api.mapreduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import io.fluo.api.config.ConnectionProperties;

import org.apache.accumulo.core.client.mapreduce.AccumuloInputFormat;
import org.apache.accumulo.core.client.mapreduce.RangeInputSplit;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Range;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import io.fluo.api.Bytes;
import io.fluo.api.ColumnIterator;
import io.fluo.api.RowIterator;
import io.fluo.api.ScannerConfiguration;
import io.fluo.impl.Configuration;
import io.fluo.impl.OracleClient;
import io.fluo.impl.TransactionImpl;

/**
 * This input format reads a consistent snapshot from a Fluo table.
 */
public class FluoInputFormat extends InputFormat<Bytes,ColumnIterator> {
  
  private static String TIMESTAMP_CONF_KEY = FluoInputFormat.class.getName() + ".timestamp";
  private static String PROPS_CONF_KEY = FluoInputFormat.class.getName() + ".props";
  private static String FAMS_CONF_KEY = FluoInputFormat.class.getName() + ".families";


  @Override
  public RecordReader<Bytes,ColumnIterator> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {

    return new RecordReader<Bytes,ColumnIterator>() {
      
      private Entry<Bytes,ColumnIterator> entry;
      private RowIterator rowIter;
      private Configuration accisConf = null;
      
      @Override
      public void close() throws IOException {
        if (accisConf != null)
          accisConf.getSharedResources().close();
      }
      
      @Override
      public Bytes getCurrentKey() throws IOException, InterruptedException {
        return entry.getKey();
      }
      
      @Override
      public ColumnIterator getCurrentValue() throws IOException, InterruptedException {
        return entry.getValue();
      }
      
      @Override
      public float getProgress() throws IOException, InterruptedException {
        // TODO Auto-generated method stub
        return 0;
      }
      
      @Override
      public void initialize(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
        try {
          // TODO this uses non public Accumulo API!
          RangeInputSplit ris = (RangeInputSplit) split;
          
          Range range = ris.getRange();
          
          ByteArrayInputStream bais = new ByteArrayInputStream(context.getConfiguration().get(PROPS_CONF_KEY).getBytes("UTF-8"));
          Properties props = new Properties();
          props.load(bais);
          
          accisConf = new Configuration(props);
          
          TransactionImpl ti = new TransactionImpl(accisConf, context.getConfiguration().getLong(TIMESTAMP_CONF_KEY, -1), null);
          ScannerConfiguration sc = new ScannerConfiguration().setRange(range);
          
          for (String fam : context.getConfiguration().getStrings(FAMS_CONF_KEY, new String[0]))
            sc.fetchColumnFamily(Bytes.wrap(fam));

          rowIter = ti.get(sc);
        } catch (Exception e) {
          throw new IOException(e);
        }
      }
      
      @Override
      public boolean nextKeyValue() throws IOException, InterruptedException {
        if (rowIter.hasNext()) {
          entry = rowIter.next();
          return true;
        }
        return false;
      }
    };

  }

  @Override
  public List<InputSplit> getSplits(JobContext context) throws IOException, InterruptedException {
    return new AccumuloInputFormat().getSplits(context);
  }
  
  /**
   * Configure properties needed to connect to a Fluo instance
   * 
   * @param conf
   * @param props
   *          use {@link io.fluo.api.config.ConnectionProperties} to configure programmatically
   */
  @SuppressWarnings("deprecation")
  public static void configure(Job conf, Properties props) {
    try {
      
      Configuration accisConf = new Configuration(props);
      
      OracleClient client = OracleClient.getInstance(accisConf);
      long ts = client.getTimestamp();
      
      conf.getConfiguration().setLong(TIMESTAMP_CONF_KEY, ts);
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      props.store(baos, "");
      
      conf.getConfiguration().set(PROPS_CONF_KEY, new String(baos.toByteArray(), "UTF8"));
      
      AccumuloInputFormat.setZooKeeperInstance(conf, props.getProperty(ConnectionProperties.ACCUMULO_INSTANCE_PROP), props.getProperty(ConnectionProperties.ZOOKEEPER_CONNECT_PROP));
      AccumuloInputFormat.setConnectorInfo(conf, props.getProperty(ConnectionProperties.ACCUMULO_USER_PROP), new PasswordToken(props.getProperty(ConnectionProperties.ACCUMULO_PASSWORD_PROP)));
      AccumuloInputFormat.setInputTableName(conf, accisConf.getTable());
      AccumuloInputFormat.setScanAuthorizations(conf, accisConf.getAuthorizations());
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // TODO support text
  public static void fetchFamilies(Job job, String... fams) {
    job.getConfiguration().setStrings(FAMS_CONF_KEY, fams);
  }

  public static void fetchFamilies(Job job, Bytes... fams) {
    // TODO support binary data
    String sfams[] = new String[fams.length];
    for (int i = 0; i < sfams.length; i++) {
      sfams[i] = fams[i].toString();
    }
    fetchFamilies(job, sfams);
  }

  // TODO let user set auths
  // TODO let user set ranges
  

}
