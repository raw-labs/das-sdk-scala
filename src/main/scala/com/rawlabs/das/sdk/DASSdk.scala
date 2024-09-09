/*
 * Copyright 2024 RAW Labs S.A.
 *
 * Use of this software is governed by the Business Source License
 * included in the file licenses/BSL.txt.
 *
 * As of the Change Date specified in that file, in accordance with
 * the Business Source License, use of this software will be governed
 * by the Apache License, Version 2.0, included in the file
 * licenses/APL.txt.
 */

package com.rawlabs.das.sdk

import com.rawlabs.protocol.das.{FunctionDefinition, Qual, Row, SortKey, TableDefinition}
import com.rawlabs.protocol.raw.Value
import com.rawlabs.utils.core.RawException

import java.io.Closeable

class DASSdkException(message: String, cause: Throwable = null) extends RawException(message, cause)

final class DASSdkUnsupportedException() extends DASSdkException("unsupported operation")

trait DASSdk {

  def tableDefinitions: Seq[TableDefinition]

  def functionDefinitions: Seq[FunctionDefinition]

  def getTable(name: String): Option[DASTable]

  def getFunction(name: String): Option[DASFunction]

}

trait DASTable {

  /**
   * Method called from the planner to estimate the resulting relation size for a scan.
   *
   * @param quals list of Qual instances describing the filters applied to this scan
   * @param columns list of columns that must be returned
   * @return tuple of the form (expected_number_of_rows, avg_row_width (in bytes))
   */
  def getRelSize(quals: Seq[Qual], columns: Seq[String]): (Int, Int)

  /**
   * Method called from the planner to ask the FDW what are the sorts it can
   * enforced, to avoid PostgreSQL to sort the data after retreiving all the
   * rows. These sorts can come from explicit ORDER BY clauses, but also GROUP
   * BY and DISTINCT clauses.
   *
   * The FDW has to inspect every sort, and respond which one are handled.
   * The sorts are cumulatives. For example::
   *
   * col1 ASC
   * col2 DESC
   *
   * means that the FDW must render the tuples sorted by col1 ascending and
   * col2 descending.
   *
   * @param sortKeys list of DASSortKey representing all the sorts the query must enforce
   * @return list of cumulative SortKey, for which the FDW can enforce the sort.
   */
  def canSort(sortKeys: Seq[SortKey]): Seq[SortKey] = Seq.empty

  /**
   * Method called from the planner to add additional Path to the planner.
   * By default, the planner generates an (unparameterized) path, which
   * can be reasoned about like a SequentialScan, optionally filtered.
   *
   * This method allows the implementor to declare other Paths,
   * corresponding to faster access methods for specific attributes.
   * Such a parameterized path can be reasoned about like an IndexScan.
   *
   * For example, with the following query::
   *
   * select * from foreign_table inner join local_table using(id);
   *
   * where foreign_table is a foreign table containing 100000 rows, and
   * local_table is a regular table containing 100 rows.
   *
   * The previous query would probably be transformed to a plan similar to
   * this one::
   *
   * ┌────────────────────────────────────────────────────────────────────────────────────┐
   * │                                     QUERY PLAN                                     │
   * ├────────────────────────────────────────────────────────────────────────────────────┤
   * │ Hash Join  (cost=57.67..4021812.67 rows=615000 width=68)                           │
   * │   Hash Cond: (foreign_table.id = local_table.id)                                   │
   * │   ->  Foreign Scan on foreign_table (cost=20.00..4000000.00 rows=100000 width=40)  │
   * │   ->  Hash  (cost=22.30..22.30 rows=1230 width=36)                                 │
   * │         ->  Seq Scan on local_table (cost=0.00..22.30 rows=1230 width=36)          │
   * └────────────────────────────────────────────────────────────────────────────────────┘
   *
   * But with a parameterized path declared on the id key, with the knowledge that this key
   * is unique on the foreign side, the following plan might get chosen::
   *
   * ┌───────────────────────────────────────────────────────────────────────┐
   * │                              QUERY PLAN                               │
   * ├───────────────────────────────────────────────────────────────────────┤
   * │ Nested Loop  (cost=20.00..49234.60 rows=615000 width=68)              │
   * │   ->  Seq Scan on local_table (cost=0.00..22.30 rows=1230 width=36)   │
   * │   ->  Foreign Scan on remote_table (cost=20.00..40.00 rows=1 width=40)│
   * │         Filter: (id = local_table.id)                                 │
   * └───────────────────────────────────────────────────────────────────────┘
   *
   * Returns:
   * A list of tuples of the form: (key_columns, expected_rows),
   * where key_columns is a tuple containing the columns on which
   * the path can be used, and expected_rows is the number of rows
   * this path might return for a simple lookup.
   * For example, the return value corresponding to the previous scenario would be::
   *
   * [(('id',), 1)]
   *
   * @return  list of tuples of the form: (key_columns, expected_rows),
   *          where key_columns is a tuple containing the columns on which
   *          the path can be used, and expected_rows is the number of rows
   *          this path might return for a simple lookup.
   */
  def getPathKeys: Seq[(Seq[String], Int)] = Seq.empty

  def explain(
      quals: Seq[Qual],
      columns: Seq[String],
      maybeSortKeys: Option[Seq[SortKey]] = None,
      maybeLimit: Option[Long] = None,
      verbose: Boolean = false
  ): Seq[String] = Seq.empty

  // SELECT
  def execute(
      quals: Seq[Qual],
      columns: Seq[String],
      maybeSortKeys: Option[Seq[SortKey]] = None,
      maybeLimit: Option[Long] = None
  ): DASExecuteResult

  def uniqueColumn: String = throw new DASSdkUnsupportedException

  def modifyBatchSize: Int = 1

  // INSERT
  def insert(row: Row): Row = throw new DASSdkUnsupportedException

  def bulkInsert(rows: Seq[Row]): Seq[Row] = throw new DASSdkUnsupportedException

  // UPDATE
  def update(rowId: Value, newValues: Row): Row = throw new DASSdkUnsupportedException

  // DELETE
  def delete(rowId: Value): Unit = throw new DASSdkUnsupportedException

}

trait DASExecuteResult extends Iterator[Row] with Closeable

trait DASFunction {

  def execute(args: Map[String, Value]): Value

}
