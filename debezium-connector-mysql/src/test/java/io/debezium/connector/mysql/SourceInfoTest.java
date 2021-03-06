/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mysql;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.kafka.connect.data.Struct;
import org.fest.assertions.GenericAssert;
import org.junit.Before;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

import io.confluent.connect.avro.AvroData;
import io.debezium.doc.FixFor;
import io.debezium.document.Document;

public class SourceInfoTest {

    private static int avroSchemaCacheSize = 1000;
    private static final AvroData avroData = new AvroData(avroSchemaCacheSize);
    private static final String FILENAME = "mysql-bin.00001";
    private static final String GTID_SET = "gtid-set"; // can technically be any string
    private static final String SERVER_NAME = "my-server"; // can technically be any string

    private SourceInfo source;
    private boolean inTxn = false;
    private long positionOfBeginEvent = 0L;
    private int eventNumberInTxn = 0;

    @Before
    public void beforeEach() {
        source = new SourceInfo();
        inTxn = false;
        positionOfBeginEvent = 0L;
        eventNumberInTxn = 0;
    }

    @Test
    public void shouldStartSourceInfoFromZeroBinlogCoordinates() {
        source.setBinlogStartPoint(FILENAME, 0);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.eventsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldStartSourceInfoFromNonZeroBinlogCoordinates() {
        source.setBinlogStartPoint(FILENAME, 100);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    // -------------------------------------------------------------------------------------
    // Test reading the offset map and recovering the proper SourceInfo state
    // -------------------------------------------------------------------------------------

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithZeroBinlogCoordinates() {
        sourceWith(offset(0, 0));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithNonZeroBinlogCoordinates() {
        sourceWith(offset(100, 0));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithZeroBinlogCoordinatesAndNonZeroRow() {
        sourceWith(offset(0, 5));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithNonZeroBinlogCoordinatesAndNonZeroRow() {
        sourceWith(offset(100, 5));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithZeroBinlogCoordinatesAndSnapshot() {
        sourceWith(offset(0, 0, true));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithNonZeroBinlogCoordinatesAndSnapshot() {
        sourceWith(offset(100, 0, true));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithZeroBinlogCoordinatesAndNonZeroRowAndSnapshot() {
        sourceWith(offset(0, 5, true));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    @Test
    public void shouldRecoverSourceInfoFromOffsetWithNonZeroBinlogCoordinatesAndNonZeroRowAndSnapshot() {
        sourceWith(offset(100, 5, true));
        assertThat(source.gtidSet()).isNull();
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndZeroBinlogCoordinates() {
        sourceWith(offset(GTID_SET, 0, 0, false));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndZeroBinlogCoordinatesAndNonZeroRow() {
        sourceWith(offset(GTID_SET, 0, 5, false));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndNonZeroBinlogCoordinates() {
        sourceWith(offset(GTID_SET, 100, 0, false));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndNonZeroBinlogCoordinatesAndNonZeroRow() {
        sourceWith(offset(GTID_SET, 100, 5, false));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isFalse();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndZeroBinlogCoordinatesAndSnapshot() {
        sourceWith(offset(GTID_SET, 0, 0, true));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndZeroBinlogCoordinatesAndNonZeroRowAndSnapshot() {
        sourceWith(offset(GTID_SET, 0, 5, true));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(0);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndNonZeroBinlogCoordinatesAndSnapshot() {
        sourceWith(offset(GTID_SET, 100, 0, true));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    @Test
    public void shouldStartSourceInfoFromBinlogCoordinatesWithGtidsAndNonZeroBinlogCoordinatesAndNonZeroRowAndSnapshot() {
        sourceWith(offset(GTID_SET, 100, 5, true));
        assertThat(source.gtidSet()).isEqualTo(GTID_SET);
        assertThat(source.binlogFilename()).isEqualTo(FILENAME);
        assertThat(source.binlogPosition()).isEqualTo(100);
        assertThat(source.rowsToSkipUponRestart()).isEqualTo(5);
        assertThat(source.isSnapshotInEffect()).isTrue();
    }

    // -------------------------------------------------------------------------------------
    // Test advancing SourceInfo state (similar to how the BinlogReader uses it)
    // -------------------------------------------------------------------------------------

    @Test
    public void shouldAdvanceSourceInfoFromNonZeroPositionAndRowZeroForEventsWithOneRow() {
        sourceWith(offset(100, 0));

        // Try a transactions with just one event ...
        handleTransactionBegin(150, 2);
        handleNextEvent(200, 10, withRowCount(1));
        handleTransactionCommit(210, 2);

        handleTransactionBegin(210, 2);
        handleNextEvent(220, 10, withRowCount(1));
        handleTransactionCommit(230, 3);

        handleTransactionBegin(240, 2);
        handleNextEvent(250, 50, withRowCount(1));
        handleTransactionCommit(300, 4);

        // Try a transactions with multiple events ...
        handleTransactionBegin(340, 2);
        handleNextEvent(350, 20, withRowCount(1));
        handleNextEvent(370, 30, withRowCount(1));
        handleNextEvent(400, 40, withRowCount(1));
        handleTransactionCommit(440, 4);

        handleTransactionBegin(500, 2);
        handleNextEvent(510, 20, withRowCount(1));
        handleNextEvent(540, 15, withRowCount(1));
        handleNextEvent(560, 10, withRowCount(1));
        handleTransactionCommit(580, 4);

        // Try another single event transaction ...
        handleTransactionBegin(600, 2);
        handleNextEvent(610, 50, withRowCount(1));
        handleTransactionCommit(660, 4);

        // Try event outside of a transaction ...
        handleNextEvent(670, 10, withRowCount(1));

        // Try another single event transaction ...
        handleTransactionBegin(700, 2);
        handleNextEvent(710, 50, withRowCount(1));
        handleTransactionCommit(760, 4);
    }

    @Test
    public void shouldAdvanceSourceInfoFromNonZeroPositionAndRowZeroForEventsWithMultipleRow() {
        sourceWith(offset(100, 0));

        // Try a transactions with just one event ...
        handleTransactionBegin(150, 2);
        handleNextEvent(200, 10, withRowCount(3));
        handleTransactionCommit(210, 2);

        handleTransactionBegin(210, 2);
        handleNextEvent(220, 10, withRowCount(4));
        handleTransactionCommit(230, 3);

        handleTransactionBegin(240, 2);
        handleNextEvent(250, 50, withRowCount(5));
        handleTransactionCommit(300, 4);

        // Try a transactions with multiple events ...
        handleTransactionBegin(340, 2);
        handleNextEvent(350, 20, withRowCount(6));
        handleNextEvent(370, 30, withRowCount(1));
        handleNextEvent(400, 40, withRowCount(3));
        handleTransactionCommit(440, 4);

        handleTransactionBegin(500, 2);
        handleNextEvent(510, 20, withRowCount(8));
        handleNextEvent(540, 15, withRowCount(9));
        handleNextEvent(560, 10, withRowCount(1));
        handleTransactionCommit(580, 4);

        // Try another single event transaction ...
        handleTransactionBegin(600, 2);
        handleNextEvent(610, 50, withRowCount(1));
        handleTransactionCommit(660, 4);

        // Try event outside of a transaction ...
        handleNextEvent(670, 10, withRowCount(5));

        // Try another single event transaction ...
        handleTransactionBegin(700, 2);
        handleNextEvent(710, 50, withRowCount(3));
        handleTransactionCommit(760, 4);
    }

    // -------------------------------------------------------------------------------------
    // Utility methods
    // -------------------------------------------------------------------------------------

    protected int withRowCount(int rowCount) {
        return rowCount;
    }

    protected void handleTransactionBegin(long positionOfEvent, int eventSize) {
        source.setEventPosition(positionOfEvent, eventSize);
        positionOfBeginEvent = positionOfEvent;
        source.startNextTransaction();
        inTxn = true;

        assertThat(source.rowsToSkipUponRestart()).isEqualTo(0);
    }

    protected void handleTransactionCommit(long positionOfEvent, int eventSize) {
        source.setEventPosition(positionOfEvent, eventSize);
        source.commitTransaction();
        eventNumberInTxn = 0;
        inTxn = false;

        // Verify the offset ...
        Map<String, ?> offset = source.offset();

        // The offset position should be the position of the next event
        long position = (Long) offset.get(SourceInfo.BINLOG_POSITION_OFFSET_KEY);
        assertThat(position).isEqualTo(positionOfEvent + eventSize);
        Long rowsToSkip = (Long) offset.get(SourceInfo.BINLOG_ROW_IN_EVENT_OFFSET_KEY);
        if (rowsToSkip == null) rowsToSkip = 0L;
        assertThat(rowsToSkip).isEqualTo(0);
        assertThat(offset.get(SourceInfo.EVENTS_TO_SKIP_OFFSET_KEY)).isNull();
        if (source.gtidSet() != null) {
            assertThat(offset.get(SourceInfo.GTID_SET_KEY)).isEqualTo(source.gtidSet());
        }
    }

    protected void handleNextEvent(long positionOfEvent, long eventSize, int rowCount) {
        if (inTxn) ++eventNumberInTxn;
        source.setEventPosition(positionOfEvent, eventSize);
        for (int row = 0; row != rowCount; ++row) {
            // Get the offset for this row (always first!) ...
            Map<String, ?> offset = source.offsetForRow(row, rowCount);
            assertThat(offset.get(SourceInfo.BINLOG_FILENAME_OFFSET_KEY)).isEqualTo(FILENAME);
            if (source.gtidSet() != null) {
                assertThat(offset.get(SourceInfo.GTID_SET_KEY)).isEqualTo(source.gtidSet());
            }
            long position = (Long) offset.get(SourceInfo.BINLOG_POSITION_OFFSET_KEY);
            if (inTxn) {
                // regardless of the row count, the position is always the txn begin position ...
                assertThat(position).isEqualTo(positionOfBeginEvent);
                // and the number of the last completed event (the previous one) ...
                Long eventsToSkip = (Long) offset.get(SourceInfo.EVENTS_TO_SKIP_OFFSET_KEY);
                if (eventsToSkip == null) eventsToSkip = 0L;
                assertThat(eventsToSkip).isEqualTo(eventNumberInTxn - 1);
            } else {
                // Matches the next event ...
                assertThat(position).isEqualTo(positionOfEvent + eventSize);
                assertThat(offset.get(SourceInfo.EVENTS_TO_SKIP_OFFSET_KEY)).isNull();
            }
            Long rowsToSkip = (Long) offset.get(SourceInfo.BINLOG_ROW_IN_EVENT_OFFSET_KEY);
            if (rowsToSkip == null) rowsToSkip = 0L;
            if( (row+1) == rowCount) {
                // This is the last row, so the next binlog position should be the number of rows in the event ...
                assertThat(rowsToSkip).isEqualTo(rowCount);
            } else {
                // This is not the last row, so the next binlog position should be the row number ...
                assertThat(rowsToSkip).isEqualTo(row+1);
            }
            // Get the source struct for this row (always second), which should always reflect this row in this event ...
            Struct recordSource = source.struct();
            assertThat(recordSource.getInt64(SourceInfo.BINLOG_POSITION_OFFSET_KEY)).isEqualTo(positionOfEvent);
            assertThat(recordSource.getInt32(SourceInfo.BINLOG_ROW_IN_EVENT_OFFSET_KEY)).isEqualTo(row);
            assertThat(recordSource.getString(SourceInfo.BINLOG_FILENAME_OFFSET_KEY)).isEqualTo(FILENAME);
            if (source.gtidSet() != null) {
                assertThat(recordSource.getString(SourceInfo.GTID_SET_KEY)).isEqualTo(source.gtidSet());
            }
        }
        source.completeEvent();
    }

    protected Map<String, String> offset(long position, int row) {
        return offset(null, position, row, false);
    }

    protected Map<String, String> offset(long position, int row, boolean snapshot) {
        return offset(null, position, row, snapshot);
    }

    protected Map<String, String> offset(String gtidSet, long position, int row, boolean snapshot) {
        Map<String, String> offset = new HashMap<>();
        offset.put(SourceInfo.BINLOG_FILENAME_OFFSET_KEY, FILENAME);
        offset.put(SourceInfo.BINLOG_POSITION_OFFSET_KEY, Long.toString(position));
        offset.put(SourceInfo.BINLOG_ROW_IN_EVENT_OFFSET_KEY, Integer.toString(row));
        if (gtidSet != null) offset.put(SourceInfo.GTID_SET_KEY, gtidSet);
        if (snapshot) offset.put(SourceInfo.SNAPSHOT_KEY, Boolean.TRUE.toString());
        return offset;
    }

    protected SourceInfo sourceWith(Map<String, String> offset) {
        source = new SourceInfo();
        source.setOffset(offset);
        source.setServerName(SERVER_NAME);
        return source;
    }

    /**
     * When we want to consume SinkRecord which generated by debezium-connector-mysql, it should not
     * throw error "org.apache.avro.SchemaParseException: Illegal character in: server-id"
     */
    @Test
    public void shouldValidateSourceInfoSchema() {
        org.apache.kafka.connect.data.Schema kafkaSchema = SourceInfo.SCHEMA;
        Schema avroSchema = avroData.fromConnectSchema(kafkaSchema);
        assertTrue(avroSchema != null);
    }

    @Test
    public void shouldConsiderPositionsWithSameGtidSetsAsSame() {
        assertPositionWithGtids("IdA:1-5").isAtOrBefore(positionWithGtids("IdA:1-5")); // same, single
        assertPositionWithGtids("IdA:1-5,IdB:1-20").isAtOrBefore(positionWithGtids("IdA:1-5,IdB:1-20")); // same, multiple
        assertPositionWithGtids("IdA:1-5,IdB:1-20").isAtOrBefore(positionWithGtids("IdB:1-20,IdA:1-5")); // equivalent
    }

    @Test
    public void shouldConsiderPositionsWithSameGtidSetsAndSnapshotAsSame() {
        assertPositionWithGtids("IdA:1-5", true).isAtOrBefore(positionWithGtids("IdA:1-5", true)); // same, single
        assertPositionWithGtids("IdA:1-5,IdB:1-20", true).isAtOrBefore(positionWithGtids("IdA:1-5,IdB:1-20", true)); // same,
                                                                                                                     // multiple
        assertPositionWithGtids("IdA:1-5,IdB:1-20", true).isAtOrBefore(positionWithGtids("IdB:1-20,IdA:1-5", true)); // equivalent
    }

    @Test
    public void shouldOrderPositionWithGtidAndSnapshotBeforePositionWithSameGtidButNoSnapshot() {
        assertPositionWithGtids("IdA:1-5", true).isAtOrBefore(positionWithGtids("IdA:1-5")); // same, single
        assertPositionWithGtids("IdA:1-5,IdB:1-20", true).isAtOrBefore(positionWithGtids("IdA:1-5,IdB:1-20")); // same, multiple
        assertPositionWithGtids("IdA:1-5,IdB:1-20", true).isAtOrBefore(positionWithGtids("IdB:1-20,IdA:1-5")); // equivalent
    }

    @Test
    public void shouldOrderPositionWithoutGtidAndSnapshotAfterPositionWithSameGtidAndSnapshot() {
        assertPositionWithGtids("IdA:1-5", false).isAfter(positionWithGtids("IdA:1-5", true)); // same, single
        assertPositionWithGtids("IdA:1-5,IdB:1-20", false).isAfter(positionWithGtids("IdA:1-5,IdB:1-20", true)); // same, multiple
        assertPositionWithGtids("IdA:1-5,IdB:1-20", false).isAfter(positionWithGtids("IdB:1-20,IdA:1-5", true)); // equivalent
    }

    @Test
    public void shouldOrderPositionWithGtidsAsBeforePositionWithExtraServerUuidInGtids() {
        assertPositionWithGtids("IdA:1-5").isBefore(positionWithGtids("IdA:1-5,IdB:1-20"));
    }

    @Test
    public void shouldOrderPositionsWithSameServerButLowerUpperLimitAsBeforePositionWithSameServerUuidInGtids() {
        assertPositionWithGtids("IdA:1-5").isBefore(positionWithGtids("IdA:1-6"));
        assertPositionWithGtids("IdA:1-5:7-9").isBefore(positionWithGtids("IdA:1-10"));
        assertPositionWithGtids("IdA:2-5:8-9").isBefore(positionWithGtids("IdA:1-10"));
    }

    @Test
    public void shouldOrderPositionWithoutGtidAsBeforePositionWithGtid() {
        assertPositionWithoutGtids("filename.01", Integer.MAX_VALUE, 0, 0).isBefore(positionWithGtids("IdA:1-5"));
    }

    @Test
    public void shouldOrderPositionWithGtidAsAfterPositionWithoutGtid() {
        assertPositionWithGtids("IdA:1-5").isAfter(positionWithoutGtids("filename.01", 0, 0, 0));
    }

    @Test
    public void shouldComparePositionsWithoutGtids() {
        // Same position ...
        assertPositionWithoutGtids("fn.01", 1, 0, 0).isAt(positionWithoutGtids("fn.01", 1, 0, 0));
        assertPositionWithoutGtids("fn.01", 1, 0, 1).isAt(positionWithoutGtids("fn.01", 1, 0, 1));
        assertPositionWithoutGtids("fn.03", 1, 0, 1).isAt(positionWithoutGtids("fn.03", 1, 0, 1));
        assertPositionWithoutGtids("fn.01", 1, 1, 0).isAt(positionWithoutGtids("fn.01", 1, 1, 0));
        assertPositionWithoutGtids("fn.01", 1, 1, 1).isAt(positionWithoutGtids("fn.01", 1, 1, 1));
        assertPositionWithoutGtids("fn.03", 1, 1, 1).isAt(positionWithoutGtids("fn.03", 1, 1, 1));

        // Before position ...
        assertPositionWithoutGtids("fn.01", 1, 0, 0).isBefore(positionWithoutGtids("fn.01", 1, 0, 1));
        assertPositionWithoutGtids("fn.01", 1, 0, 0).isBefore(positionWithoutGtids("fn.01", 2, 0, 0));
        assertPositionWithoutGtids("fn.01", 1, 0, 1).isBefore(positionWithoutGtids("fn.01", 1, 0, 2));
        assertPositionWithoutGtids("fn.01", 1, 0, 1).isBefore(positionWithoutGtids("fn.01", 2, 0, 0));
        assertPositionWithoutGtids("fn.01", 1, 1, 0).isBefore(positionWithoutGtids("fn.01", 1, 1, 1));
        assertPositionWithoutGtids("fn.01", 1, 1, 0).isBefore(positionWithoutGtids("fn.01", 1, 2, 0));
        assertPositionWithoutGtids("fn.01", 1, 1, 1).isBefore(positionWithoutGtids("fn.01", 1, 2, 0));
        assertPositionWithoutGtids("fn.01", 1, 1, 1).isBefore(positionWithoutGtids("fn.01", 2, 0, 0));

        // After position ...
        assertPositionWithoutGtids("fn.01", 1, 0, 1).isAfter(positionWithoutGtids("fn.01", 0, 0, 99));
        assertPositionWithoutGtids("fn.01", 1, 0, 1).isAfter(positionWithoutGtids("fn.01", 1, 0, 0));
        assertPositionWithoutGtids("fn.01", 1, 1, 1).isAfter(positionWithoutGtids("fn.01", 0, 0, 99));
        assertPositionWithoutGtids("fn.01", 1, 1, 1).isAfter(positionWithoutGtids("fn.01", 1, 0, 0));
        assertPositionWithoutGtids("fn.01", 1, 1, 1).isAfter(positionWithoutGtids("fn.01", 1, 1, 0));
    }

    @FixFor("DBZ-107")
    @Test
    public void shouldRemoveNewlinesFromGtidSet() {
        String gtidExecuted = "036d85a9-64e5-11e6-9b48-42010af0000c:1-2,\n" +
                "7145bf69-d1ca-11e5-a588-0242ac110004:1-3149,\n" +
                "7c1de3f2-3fd2-11e6-9cdc-42010af000bc:1-39";
        String gtidCleaned = "036d85a9-64e5-11e6-9b48-42010af0000c:1-2," +
                "7145bf69-d1ca-11e5-a588-0242ac110004:1-3149," +
                "7c1de3f2-3fd2-11e6-9cdc-42010af000bc:1-39";
        source.setCompletedGtidSet(gtidExecuted);
        assertThat(source.gtidSet()).isEqualTo(gtidCleaned);
    }

    @FixFor("DBZ-107")
    @Test
    public void shouldNotSetBlankGtidSet() {
        source.setCompletedGtidSet("");
        assertThat(source.gtidSet()).isNull();
    }

    @FixFor("DBZ-107")
    @Test
    public void shouldNotSetNullGtidSet() {
        source.setCompletedGtidSet(null);
        assertThat(source.gtidSet()).isNull();
    }

    protected Document positionWithGtids(String gtids) {
        return positionWithGtids(gtids, false);
    }

    protected Document positionWithGtids(String gtids, boolean snapshot) {
        if (snapshot) {
            return Document.create(SourceInfo.GTID_SET_KEY, gtids, SourceInfo.SNAPSHOT_KEY, true);
        }
        return Document.create(SourceInfo.GTID_SET_KEY, gtids);
    }

    protected Document positionWithoutGtids(String filename, int position, int event, int row) {
        return positionWithoutGtids(filename, position, event, row, false);
    }

    protected Document positionWithoutGtids(String filename, int position, int event, int row, boolean snapshot) {
        if (snapshot) {
            return Document.create(SourceInfo.BINLOG_FILENAME_OFFSET_KEY, filename,
                                   SourceInfo.BINLOG_POSITION_OFFSET_KEY, position,
                                   SourceInfo.BINLOG_ROW_IN_EVENT_OFFSET_KEY, row,
                                   SourceInfo.EVENTS_TO_SKIP_OFFSET_KEY, event,
                                   SourceInfo.SNAPSHOT_KEY, true);
        }
        return Document.create(SourceInfo.BINLOG_FILENAME_OFFSET_KEY, filename,
                               SourceInfo.BINLOG_POSITION_OFFSET_KEY, position,
                               SourceInfo.BINLOG_ROW_IN_EVENT_OFFSET_KEY, row,
                               SourceInfo.EVENTS_TO_SKIP_OFFSET_KEY, event);
    }

    protected PositionAssert assertThatDocument(Document position) {
        return new PositionAssert(position);
    }

    protected PositionAssert assertPositionWithGtids(String gtids) {
        return assertThatDocument(positionWithGtids(gtids));
    }

    protected PositionAssert assertPositionWithGtids(String gtids, boolean snapshot) {
        return assertThatDocument(positionWithGtids(gtids, snapshot));
    }

    protected PositionAssert assertPositionWithoutGtids(String filename, int position, int event, int row) {
        return assertPositionWithoutGtids(filename, position, event, row, false);
    }

    protected PositionAssert assertPositionWithoutGtids(String filename, int position, int event, int row, boolean snapshot) {
        return assertThatDocument(positionWithoutGtids(filename, position, event, row, snapshot));
    }

    protected static class PositionAssert extends GenericAssert<PositionAssert, Document> {
        public PositionAssert(Document position) {
            super(PositionAssert.class, position);
        }

        public PositionAssert isAt(Document otherPosition) {
            if (SourceInfo.isPositionAtOrBefore(actual, otherPosition)) return this;
            failIfCustomMessageIsSet();
            throw failure(actual + " should be consider same position as " + otherPosition);
        }

        public PositionAssert isBefore(Document otherPosition) {
            return isAtOrBefore(otherPosition);
        }

        public PositionAssert isAtOrBefore(Document otherPosition) {
            if (SourceInfo.isPositionAtOrBefore(actual, otherPosition)) return this;
            failIfCustomMessageIsSet();
            throw failure(actual + " should be consider same position as or before " + otherPosition);
        }

        public PositionAssert isAfter(Document otherPosition) {
            if (!SourceInfo.isPositionAtOrBefore(actual, otherPosition)) return this;
            failIfCustomMessageIsSet();
            throw failure(actual + " should be consider after " + otherPosition);
        }
    }
}
