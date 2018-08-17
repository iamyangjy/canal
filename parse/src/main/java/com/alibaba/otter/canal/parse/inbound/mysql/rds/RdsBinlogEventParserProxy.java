package com.alibaba.otter.canal.parse.inbound.mysql.rds;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.lang.StringUtils;

import com.alibaba.otter.canal.parse.exception.PositionNotFoundException;
import com.alibaba.otter.canal.parse.inbound.ParserExceptionHandler;
import com.alibaba.otter.canal.parse.inbound.mysql.MysqlEventParser;

/**
 * aliyun rds的binlog parser支持
 * 
 * <pre>
 * 注意点：aliyun的binlog会有定期清理并备份到oss上, 这里实现了一份自动下载oss+rds binlog的机制
 * </pre>
 * 
 * @author chengjin.lyf on 2018/7/20 上午10:52
 * @since 1.0.25
 */
public class RdsBinlogEventParserProxy extends MysqlEventParser {

    private String                    rdsOpenApiUrl             = "https://rds.aliyuncs.com/";    // openapi地址
    private String                    accesskey;                                                  // 云账号的ak
    private String                    secretkey;                                                  // 云账号sk
    private String                    instanceId;                                                 // rds实例id
    private String                    directory;                                                  // binlog目录
    private int                       batchFileSize             = 4;                              // 最多下载的binlog文件数量

    private RdsLocalBinlogEventParser rdsLocalBinlogEventParser = new RdsLocalBinlogEventParser();
    private ExecutorService           executorService           = Executors.newSingleThreadExecutor(new ThreadFactory() {

                                                                    @Override
                                                                    public Thread newThread(Runnable r) {
                                                                        Thread t = new Thread(r,
                                                                            "rds-binlog-daemon-thread");
                                                                        t.setDaemon(true);
                                                                        return t;
                                                                    }
                                                                });

    @Override
    public void start() {
        if (StringUtils.isNotEmpty(accesskey) && StringUtils.isNotEmpty(secretkey)
            && StringUtils.isNotEmpty(instanceId)) {
            final ParserExceptionHandler targetHandler = this.getParserExceptionHandler();
            if (directory == null) {
                directory = System.getProperty("java.io.tmpdir", "/tmp") + "/" + destination;
            }
            rdsLocalBinlogEventParser.setLogPositionManager(this.getLogPositionManager());
            rdsLocalBinlogEventParser.setDestination(destination);
            rdsLocalBinlogEventParser.setAlarmHandler(this.getAlarmHandler());
            rdsLocalBinlogEventParser.setConnectionCharset(this.connectionCharset);
            rdsLocalBinlogEventParser.setConnectionCharsetNumber(this.connectionCharsetNumber);
            rdsLocalBinlogEventParser.setEnableTsdb(this.enableTsdb);
            rdsLocalBinlogEventParser.setEventBlackFilter(this.eventBlackFilter);
            rdsLocalBinlogEventParser.setFilterQueryDcl(this.filterQueryDcl);
            rdsLocalBinlogEventParser.setFilterQueryDdl(this.filterQueryDdl);
            rdsLocalBinlogEventParser.setFilterQueryDml(this.filterQueryDml);
            rdsLocalBinlogEventParser.setFilterRows(this.filterRows);
            rdsLocalBinlogEventParser.setFilterTableError(this.filterTableError);
            // rdsLocalBinlogEventParser.setIsGTIDMode(this.isGTIDMode);
            rdsLocalBinlogEventParser.setMasterInfo(this.masterInfo);
            rdsLocalBinlogEventParser.setEventFilter(this.eventFilter);
            rdsLocalBinlogEventParser.setMasterPosition(this.masterPosition);
            rdsLocalBinlogEventParser.setTransactionSize(this.transactionSize);
            rdsLocalBinlogEventParser.setUrl(this.rdsOpenApiUrl);
            rdsLocalBinlogEventParser.setAccesskey(this.accesskey);
            rdsLocalBinlogEventParser.setSecretkey(this.secretkey);
            rdsLocalBinlogEventParser.setInstanceId(this.instanceId);
            rdsLocalBinlogEventParser.setEventSink(eventSink);
            rdsLocalBinlogEventParser.setDirectory(directory);
            rdsLocalBinlogEventParser.setBatchFileSize(batchFileSize);
            rdsLocalBinlogEventParser.setParallel(this.parallel);
            rdsLocalBinlogEventParser.setParallelBufferSize(this.parallelBufferSize);
            rdsLocalBinlogEventParser.setParallelThreadSize(this.parallelThreadSize);
            rdsLocalBinlogEventParser.setFinishListener(new RdsLocalBinlogEventParser.ParseFinishListener() {

                @Override
                public void onFinish() {
                    executorService.execute(new Runnable() {

                        @Override
                        public void run() {
                            rdsLocalBinlogEventParser.stop();
                            RdsBinlogEventParserProxy.this.start();
                        }
                    });

                }
            });
            this.setParserExceptionHandler(new ParserExceptionHandler() {

                @Override
                public void handle(Throwable e) {
                    handleMysqlParserException(e);
                    if (targetHandler != null) {
                        targetHandler.handle(e);
                    }
                }
            });
        }

        super.start();
    }

    private void handleMysqlParserException(Throwable throwable) {
        if (throwable instanceof PositionNotFoundException) {
            logger.info("remove rds not found position, try download rds binlog!");
            executorService.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        logger.info("stop mysql parser!");
                        RdsBinlogEventParserProxy rdsBinlogEventParserProxy = RdsBinlogEventParserProxy.this;
                        long serverId = rdsBinlogEventParserProxy.getServerId();
                        rdsLocalBinlogEventParser.setServerId(serverId);
                        rdsBinlogEventParserProxy.stop();
                        logger.info("start rds mysql binlog parser!");
                        rdsLocalBinlogEventParser.start();
                    } catch (Throwable e) {
                        logger.info("handle exception failed", e);
                    }
                }
            });
        }
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public boolean isStart() {
        return super.isStart();
    }

    public void setRdsOpenApiUrl(String rdsOpenApiUrl) {
        this.rdsOpenApiUrl = rdsOpenApiUrl;
    }

    public void setAccesskey(String accesskey) {
        this.accesskey = accesskey;
    }

    public void setSecretkey(String secretkey) {
        this.secretkey = secretkey;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public void setBatchFileSize(int batchFileSize) {
        this.batchFileSize = batchFileSize;
    }

}
