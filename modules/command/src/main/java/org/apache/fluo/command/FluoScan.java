/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.fluo.command;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.fluo.api.config.FluoConfiguration;
import org.apache.fluo.core.client.FluoAdminImpl;
import org.apache.fluo.core.util.ScanUtil;
import org.apache.fluo.core.util.ScanUtil.ScanFlags;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

@Parameters(commandNames = "scan", commandDescription = "Prints snapshot of data in Fluo <app>")
public class FluoScan extends AppCommand {

  @Parameter(names = "-s", description = "Start row (inclusive) of scan")
  private String startRow;

  @Parameter(names = "-e", description = "End row (inclusive) of scan")
  private String endRow;

  @Parameter(names = "-c", description = "Columns of scan in comma separated format: "
      + "<<columnfamily>[:<columnqualifier>]{,<columnfamily>[:<columnqualifier>]}> ")
  private List<String> columns;

  @Parameter(names = "-r", description = "Exact row to scan")
  private String exactRow;

  @Parameter(names = "-p", description = "Row prefix to scan")
  private String rowPrefix;

  @Parameter(names = {"-esc", "--escape-non-ascii"}, help = true,
      description = "Hex encode non ascii bytes", arity = 1)
  public boolean hexEncNonAscii = true;

  @Parameter(names = "--raw", help = true,
      description = "Show underlying key/values stored in Accumulo. Interprets the data using Fluo "
          + "internal schema, making it easier to comprehend.")
  public boolean scanAccumuloTable = false;

  @Parameter(names = "--json", help = true,
      description = "Export key/values stored in Accumulo as JSON file.")
  public boolean exportAsJson = false;

  @Parameter(names = "--ntfy", help = true, description = "Scan active notifications")
  public boolean scanNtfy = false;

  /**
   * Check if the parameters informed can be used together.
   */
  private void checkScanOptions() {
    if (this.scanAccumuloTable && this.exportAsJson) {
      throw new FluoCommandException("Both \"--raw\" and \"--json\" can not be set together.");
    }

    if (this.scanAccumuloTable && this.scanNtfy) {
      throw new FluoCommandException("Both \"--raw\" and \"--ntfy\" can not be set together.");
    }
  }

  public ScanUtil.ScanOpts getScanOpts() {
    EnumSet<ScanFlags> flags = EnumSet.noneOf(ScanFlags.class);

    ScanUtil.setFlag(flags, isHelp(), ScanFlags.HELP);
    ScanUtil.setFlag(flags, hexEncNonAscii, ScanFlags.HEX);
    ScanUtil.setFlag(flags, scanAccumuloTable, ScanFlags.ACCUMULO);
    ScanUtil.setFlag(flags, exportAsJson, ScanFlags.JSON);
    ScanUtil.setFlag(flags, scanNtfy, ScanFlags.NTFY);

    return new ScanUtil.ScanOpts(startRow, endRow, columns, exactRow, rowPrefix, flags);
  }

  @Override
  public void execute() throws FluoCommandException {
    Logger.getRootLogger().setLevel(Level.ERROR);
    Logger.getLogger("org.apache.fluo").setLevel(Level.ERROR);

    checkScanOptions();
    FluoConfiguration config = getConfig();

    try {
      if (scanAccumuloTable) {
        config = FluoAdminImpl.mergeZookeeperConfig(config);
        ScanUtil.scanAccumulo(getScanOpts(), config, System.out);
      } else if (scanNtfy) {
        config = FluoAdminImpl.mergeZookeeperConfig(config);
        ScanUtil.scanNotifications(getScanOpts(), config, System.out);
      } else {
        CommandUtil.verifyAppRunning(config);
        ScanUtil.scanFluo(getScanOpts(), config, System.out);
      }
    } catch (IOException e) {
      throw new FluoCommandException(e);
    }
  }

  public String getStartRow() {
    return startRow;
  }

  public String getEndRow() {
    return endRow;
  }

  public String getExactRow() {
    return exactRow;
  }

  public String getRowPrefix() {
    return rowPrefix;
  }

  public List<String> getColumns() {
    if (columns == null) {
      return Collections.emptyList();
    }
    return columns;
  }
}
