package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

/**
 * Contains all databases necessary for full-text search (FTS).
 */
@SuppressLint({ "RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage"}) // Handles updates via triggers
public class SearchTable extends DatabaseTable {

  public static final String SMS_FTS_TABLE_NAME = "sms_fts";
  public static final String MMS_FTS_TABLE_NAME = "mms_fts";

  public static final String ID                     = "rowid";
  public static final String BODY                   = MmsSmsColumns.BODY;
  public static final String THREAD_ID              = MmsSmsColumns.THREAD_ID;
  public static final String SNIPPET                = "snippet";
  public static final String CONVERSATION_RECIPIENT = "conversation_recipient";
  public static final String MESSAGE_RECIPIENT      = "message_recipient";
  public static final String IS_MMS                 = "is_mms";
  public static final String MESSAGE_ID             = "message_id";

  public static final String SNIPPET_WRAP = "...";

  public static final String[] CREATE_TABLE = {
      "CREATE VIRTUAL TABLE " + SMS_FTS_TABLE_NAME + " USING fts5(" + BODY + ", " + THREAD_ID + " UNINDEXED, content=" + SmsTable.TABLE_NAME + ", content_rowid=" + SmsTable.ID + ");",

      "CREATE TRIGGER sms_ai AFTER INSERT ON " + SmsTable.TABLE_NAME + " BEGIN\n" +
      "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES (new." + SmsTable.ID + ", new." + SmsTable.BODY + ", new." + SmsTable.THREAD_ID + ");\n" +
      "END;\n",
      "CREATE TRIGGER sms_ad AFTER DELETE ON " + SmsTable.TABLE_NAME + " BEGIN\n" +
      "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + SMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + SmsTable.ID + ", old." + SmsTable.BODY + ", old." + SmsTable.THREAD_ID + ");\n" +
      "END;\n",
      "CREATE TRIGGER sms_au AFTER UPDATE ON " + SmsTable.TABLE_NAME + " BEGIN\n" +
      "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + SMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + SmsTable.ID + ", old." + SmsTable.BODY + ", old." + SmsTable.THREAD_ID + ");\n" +
      "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES(new." + SmsTable.ID + ", new." + SmsTable.BODY + ", new." + SmsTable.THREAD_ID + ");\n" +
      "END;",


      "CREATE VIRTUAL TABLE " + MMS_FTS_TABLE_NAME + " USING fts5(" + BODY + ", " + THREAD_ID + " UNINDEXED, content=" + MmsTable.TABLE_NAME + ", content_rowid=" + MmsTable.ID + ");",

      "CREATE TRIGGER mms_ai AFTER INSERT ON " + MmsTable.TABLE_NAME + " BEGIN\n" +
      "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES (new." + MmsTable.ID + ", new." + MmsTable.BODY + ", new." + MmsTable.THREAD_ID + ");\n" +
      "END;\n",
      "CREATE TRIGGER mms_ad AFTER DELETE ON " + MmsTable.TABLE_NAME + " BEGIN\n" +
      "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + MMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + MmsTable.ID + ", old." + MmsTable.BODY + ", old." + MmsTable.THREAD_ID + ");\n" +
      "END;\n",
      "CREATE TRIGGER mms_au AFTER UPDATE ON " + MmsTable.TABLE_NAME + " BEGIN\n" +
      "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + MMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + MmsTable.ID + ", old." + MmsTable.BODY + ", old." + MmsTable.THREAD_ID + ");\n" +
      "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES (new." + MmsTable.ID + ", new." + MmsTable.BODY + ", new." + MmsTable.THREAD_ID + ");\n" +
      "END;"
  };

  private static final String MESSAGES_QUERY =
      "SELECT " +
      ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
      MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
      "snippet(" + SMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
      SmsTable.TABLE_NAME + "." + SmsTable.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
      SMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
      SMS_FTS_TABLE_NAME + "." + BODY + ", " +
      SMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
      "0 AS " + IS_MMS + " " +
      "FROM " + SmsTable.TABLE_NAME + " " +
      "INNER JOIN " + SMS_FTS_TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + ID + " = " + SmsTable.TABLE_NAME + "." + SmsTable.ID + " " +
      "INNER JOIN " + ThreadTable.TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.ID + " " +
      "WHERE " + SMS_FTS_TABLE_NAME + " MATCH ? " +
      "AND " + SmsTable.TABLE_NAME + "." + SmsTable.TYPE + " & " + MmsSmsColumns.Types.GROUP_V2_BIT + " = 0 " +
      "AND " + SmsTable.TABLE_NAME + "." + SmsTable.TYPE + " & " + MmsSmsColumns.Types.BASE_TYPE_MASK + " != " + MmsSmsColumns.Types.PROFILE_CHANGE_TYPE + " " +
      "AND " + SmsTable.TABLE_NAME + "." + SmsTable.TYPE + " & " + MmsSmsColumns.Types.BASE_TYPE_MASK + " != " + MmsSmsColumns.Types.GROUP_CALL_TYPE + " " +
      "UNION ALL " +
      "SELECT " +
      ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
      MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
      "snippet(" + MMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
      MmsTable.TABLE_NAME + "." + MmsTable.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
      MMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
      MMS_FTS_TABLE_NAME + "." + BODY + ", " +
      MMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
      "1 AS " + IS_MMS + " " +
      "FROM " + MmsTable.TABLE_NAME + " " +
      "INNER JOIN " + MMS_FTS_TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + ID + " = " + MmsTable.TABLE_NAME + "." + MmsTable.ID + " " +
      "INNER JOIN " + ThreadTable.TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.ID + " " +
      "WHERE " + MMS_FTS_TABLE_NAME + " MATCH ? " +
      "AND " + MmsTable.TABLE_NAME + "." + MmsTable.MESSAGE_BOX + " & " + MmsSmsColumns.Types.GROUP_V2_BIT + " = 0 " +
      "AND " + MmsTable.TABLE_NAME + "." + MmsTable.MESSAGE_BOX + " & " + MmsSmsColumns.Types.SPECIAL_TYPE_PAYMENTS_NOTIFICATION + " = 0 " +
      "ORDER BY " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC " +
      "LIMIT 500";

  private static final String MESSAGES_FOR_THREAD_QUERY =
      "SELECT " +
      ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
      MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
      "snippet(" + SMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
      SmsTable.TABLE_NAME + "." + SmsTable.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
      SMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
      SMS_FTS_TABLE_NAME + "." + BODY + ", " +
      SMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
      "0 AS " + IS_MMS + " " +
      "FROM " + SmsTable.TABLE_NAME + " " +
      "INNER JOIN " + SMS_FTS_TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + ID + " = " + SmsTable.TABLE_NAME + "." + SmsTable.ID + " " +
      "INNER JOIN " + ThreadTable.TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.ID + " " +
      "WHERE " + SMS_FTS_TABLE_NAME + " MATCH ? AND " + SmsTable.TABLE_NAME + "." + MmsSmsColumns.THREAD_ID + " = ? " +
      "UNION ALL " +
      "SELECT " +
      ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
      MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
      "snippet(" + MMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
      MmsTable.TABLE_NAME + "." + MmsTable.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
      MMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
      MMS_FTS_TABLE_NAME + "." + BODY + ", " +
      MMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
      "1 AS " + IS_MMS + " " +
      "FROM " + MmsTable.TABLE_NAME + " " +
      "INNER JOIN " + MMS_FTS_TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + ID + " = " + MmsTable.TABLE_NAME + "." + MmsTable.ID + " " +
      "INNER JOIN " + ThreadTable.TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.ID + " " +
      "WHERE " + MMS_FTS_TABLE_NAME + " MATCH ? AND " + MmsTable.TABLE_NAME + "." + MmsSmsColumns.THREAD_ID + " = ? " +
      "ORDER BY " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC " +
      "LIMIT 500";

  public SearchTable(@NonNull Context context, @NonNull SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor queryMessages(@NonNull String query) {
    SQLiteDatabase db                  = databaseHelper.getSignalReadableDatabase();
    String         fullTextSearchQuery = createFullTextSearchQuery(query);

    if (TextUtils.isEmpty(fullTextSearchQuery)) {
      return null;
    }

    return db.rawQuery(MESSAGES_QUERY, new String[] { fullTextSearchQuery, fullTextSearchQuery });
  }

  public Cursor queryMessages(@NonNull String query, long threadId) {
    SQLiteDatabase db                  = databaseHelper.getSignalReadableDatabase();
    String         fullTextSearchQuery = createFullTextSearchQuery(query);

    if (TextUtils.isEmpty(fullTextSearchQuery)) {
      return null;
    }

    return db.rawQuery(MESSAGES_FOR_THREAD_QUERY, new String[] { fullTextSearchQuery,
                                                                 String.valueOf(threadId),
                                                                 fullTextSearchQuery,
                                                                 String.valueOf(threadId) });
  }

  private static String createFullTextSearchQuery(@NonNull String query) {
    return Stream.of(query.split(" "))
                 .map(String::trim)
                 .filter(s -> s.length() > 0)
                 .map(SearchTable::fullTextSearchEscape)
                 .collect(StringBuilder::new, (sb, s) -> sb.append(s).append("* "))
                 .toString();
  }

  private static String fullTextSearchEscape(String s) {
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}

