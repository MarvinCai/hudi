package com.uber.hoodie.utilities.sources;

import com.uber.hoodie.DataSourceReadOptions;
import com.uber.hoodie.DataSourceUtils;
import com.uber.hoodie.common.model.HoodieRecord;
import com.uber.hoodie.common.util.TypedProperties;
import com.uber.hoodie.common.util.collection.Pair;
import com.uber.hoodie.hive.SlashEncodedDayPartitionValueExtractor;
import com.uber.hoodie.utilities.schema.SchemaProvider;
import com.uber.hoodie.utilities.sources.helpers.IncrSourceHelper;
import java.util.Arrays;
import java.util.Optional;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.DataFrameReader;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public class HoodieIncrSource extends RowSource {

  /**
   * Configs supported
   */
  protected static class Config {

    /**
     * {@value #HOODIE_SRC_BASE_PATH} is the base-path for the source Hoodie table
     */
    private static final String HOODIE_SRC_BASE_PATH = "hoodie.deltastreamer.source.hoodieincr.path";

    /**
     * {@value #NUM_INSTANTS_PER_FETCH} allows the max number of instants whose changes can be incrementally fetched
     */
    private static final String NUM_INSTANTS_PER_FETCH = "hoodie.deltastreamer.source.hoodieincr.num_instants";
    private static final Integer DEFAULT_NUM_INSTANTS_PER_FETCH = 1;

    /**
     * {@value #HOODIE_SRC_PARTITION_FIELDS} specifies partition fields that needs to be added to source table after
     * parsing _hoodie_partition_path
     */
    private static final String HOODIE_SRC_PARTITION_FIELDS = "hoodie.deltastreamer.source.hoodieincr.partition.fields";

    /**
     * {@value #HOODIE_SRC_PARTITION_EXTRACTORCLASS} PartitionValueExtractor class to extract partition fields from
     * _hoodie_partition_path
     */
    private static final String HOODIE_SRC_PARTITION_EXTRACTORCLASS =
        "hoodie.deltastreamer.source.hoodieincr.partition.extractor.class";
    private static final String DEFAULT_HOODIE_SRC_PARTITION_EXTRACTORCLASS =
        SlashEncodedDayPartitionValueExtractor.class.getCanonicalName();

    /**
     * {@value #READ_LATEST_INSTANT_ON_MISSING_CKPT} allows delta-streamer to incrementally fetch from latest committed
     * instant when checkpoint is not provided.
     */
    private static final String READ_LATEST_INSTANT_ON_MISSING_CKPT =
        "hoodie.deltastreamer.source.hoodieincr.read_latest_on_missing_ckpt";
    private static final Boolean DEFAULT_READ_LATEST_INSTANT_ON_MISSING_CKPT = false;
  }

  public HoodieIncrSource(TypedProperties props,
      JavaSparkContext sparkContext, SparkSession sparkSession,
      SchemaProvider schemaProvider) {
    super(props, sparkContext, sparkSession, schemaProvider);
  }

  @Override
  public Pair<Optional<Dataset<Row>>, String> fetchNextBatch(Optional<String> lastCkptStr, long sourceLimit) {

    DataSourceUtils.checkRequiredProperties(props, Arrays.asList(Config.HOODIE_SRC_BASE_PATH));

    /**
     DataSourceUtils.checkRequiredProperties(props, Arrays.asList(Config.HOODIE_SRC_BASE_PATH,
     Config.HOODIE_SRC_PARTITION_FIELDS));
    List<String> partitionFields = props.getStringList(Config.HOODIE_SRC_PARTITION_FIELDS, ",",
        new ArrayList<>());
    PartitionValueExtractor extractor = DataSourceUtils.createPartitionExtractor(props.getString(
        Config.HOODIE_SRC_PARTITION_EXTRACTORCLASS, Config.DEFAULT_HOODIE_SRC_PARTITION_EXTRACTORCLASS));
    **/
    String srcPath = props.getString(Config.HOODIE_SRC_BASE_PATH);
    int numInstantsPerFetch = props.getInteger(Config.NUM_INSTANTS_PER_FETCH, Config.DEFAULT_NUM_INSTANTS_PER_FETCH);
    boolean readLatestOnMissingCkpt = props.getBoolean(Config.READ_LATEST_INSTANT_ON_MISSING_CKPT,
        Config.DEFAULT_READ_LATEST_INSTANT_ON_MISSING_CKPT);

    // Use begin Instant if set and non-empty
    Optional<String> beginInstant =
        lastCkptStr.isPresent() ? lastCkptStr.get().isEmpty() ? Optional.empty() : lastCkptStr : Optional.empty();

    Pair<String, String> instantEndpts = IncrSourceHelper.calculateBeginAndEndInstants(sparkContext, srcPath,
        numInstantsPerFetch, beginInstant, readLatestOnMissingCkpt);

    if (instantEndpts.getKey().equals(instantEndpts.getValue())) {
      log.warn("Already caught up. Begin Checkpoint was :" + instantEndpts.getKey());
      return Pair.of(Optional.empty(), instantEndpts.getKey());
    }

    // Do Incr pull. Set end instant if available
    DataFrameReader reader = sparkSession.read().format("com.uber.hoodie")
        .option(DataSourceReadOptions.VIEW_TYPE_OPT_KEY(), DataSourceReadOptions.VIEW_TYPE_INCREMENTAL_OPT_VAL())
        .option(DataSourceReadOptions.BEGIN_INSTANTTIME_OPT_KEY(), instantEndpts.getLeft())
        .option(DataSourceReadOptions.END_INSTANTTIME_OPT_KEY(), instantEndpts.getRight());

    Dataset<Row> source = reader.load(srcPath);

    /**
    log.info("Partition Fields are : (" + partitionFields + "). Initial Source Schema :" + source.schema());

    StructType newSchema = new StructType(source.schema().fields());
    for (String field : partitionFields) {
      newSchema = newSchema.add(field, DataTypes.StringType, true);
    }

    /**
     * Validates if the commit time is sane and also generates Partition fields from _hoodie_partition_path if
     * configured
     *
    Dataset<Row> validated = source.map((MapFunction<Row, Row>) (Row row) -> {
      // _hoodie_instant_time
      String instantTime = row.getString(0);
      IncrSourceHelper.validateInstantTime(row, instantTime, instantEndpts.getKey(), instantEndpts.getValue());
      if (!partitionFields.isEmpty()) {
        // _hoodie_partition_path
        String hoodiePartitionPath = row.getString(3);
        List<Object> partitionVals = extractor.extractPartitionValuesInPath(hoodiePartitionPath).stream()
            .map(o -> (Object) o).collect(Collectors.toList());
        Preconditions.checkArgument(partitionVals.size() == partitionFields.size(),
            "#partition-fields != #partition-values-extracted");
        List<Object> rowObjs = new ArrayList<>(scala.collection.JavaConversions.seqAsJavaList(row.toSeq()));
        rowObjs.addAll(partitionVals);
        return RowFactory.create(rowObjs.toArray());
      }
      return row;
    }, RowEncoder.apply(newSchema));

    log.info("Validated Source Schema :" + validated.schema());
    **/

    // Remove Hoodie meta columns except partition path from input source
    final Dataset<Row> src = source.drop(HoodieRecord.HOODIE_META_COLUMNS.stream()
        .filter(x -> !x.equals(HoodieRecord.PARTITION_PATH_METADATA_FIELD)).toArray(String[]::new));
    //log.info("Final Schema from Source is :" + src.schema());
    return Pair.of(Optional.of(src), instantEndpts.getRight());
  }
}