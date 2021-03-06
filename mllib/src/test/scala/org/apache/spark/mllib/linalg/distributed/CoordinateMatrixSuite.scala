/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.mllib.linalg.distributed

import breeze.linalg.{DenseMatrix => BDM}

import org.apache.spark.SparkFunSuite
import org.apache.spark.mllib.util.MLlibTestSparkContext
import org.apache.spark.mllib.linalg.Vectors
/**
 * 坐标矩阵(CoordinateMatrix)也是由RDD做底层结构的分布式矩阵
 * CoordinateMatrix是由MatrixEntry组成的矩阵,一个MatrixEntry代表矩阵的一个元素,元素由它的行列索引表示
 */
class CoordinateMatrixSuite extends SparkFunSuite with MLlibTestSparkContext {

  val m = 5
  val n = 4
  //CoordinateMatrix常用于稀疏性比较高的计算中,MatrixEntry是一个 Tuple类型的元素,其中包含行、列和元素值
  var mat: CoordinateMatrix = _

  override def beforeAll() {
    super.beforeAll()
    val entries = sc.parallelize(Seq(
      (0, 0, 1.0),
      (0, 1, 2.0),
      (1, 1, 3.0),
      (1, 2, 4.0),
      (2, 2, 5.0),
      (2, 3, 6.0),
      (3, 0, 7.0),
      (3, 3, 8.0),
      (4, 1, 9.0)), 3).map { case (i, j, value) =>
      MatrixEntry(i, j, value)
    }
    mat = new CoordinateMatrix(entries)
  }

  test("size") {
    assert(mat.numRows() === m)
    assert(mat.numCols() === n)
  }

  test("empty entries") {//空项
    val entries = sc.parallelize(Seq[MatrixEntry](), 1)
    //CoordinateMatrix常用于稀疏性比较高的计算中,MatrixEntry是一个 Tuple类型的元素,其中包含行、列和元素值
    val emptyMat = new CoordinateMatrix(entries)
    intercept[RuntimeException] {
      emptyMat.numCols()
    }
    intercept[RuntimeException] {
      emptyMat.numRows()
    }
  }

  test("toBreeze") {
    val expected = BDM(
      (1.0, 2.0, 0.0, 0.0),
      (0.0, 3.0, 4.0, 0.0),
      (0.0, 0.0, 5.0, 6.0),
      (7.0, 0.0, 0.0, 8.0),
      (0.0, 9.0, 0.0, 0.0))
    assert(mat.toBreeze() === expected)
  }

  test("transpose") {//转置矩阵
    val transposed = mat.transpose()
    assert(mat.toBreeze().t === transposed.toBreeze())
  }

  test("toIndexedRowMatrix") {
  //索引行矩阵(IndexedRowMatrix)按行分布式存储,有行索引,其底层支撑结构是索引的行组成的RDD,所以每行可以通过索引(long)和局部向量表示
    val indexedRowMatrix = mat.toIndexedRowMatrix()
    val expected = BDM(
      (1.0, 2.0, 0.0, 0.0),
      (0.0, 3.0, 4.0, 0.0),
      (0.0, 0.0, 5.0, 6.0),
      (7.0, 0.0, 0.0, 8.0),
      (0.0, 9.0, 0.0, 0.0))
    assert(indexedRowMatrix.toBreeze() === expected)
  }

  test("toRowMatrix") {//行矩阵
    val rowMatrix = mat.toRowMatrix()
    val rows = rowMatrix.rows.collect().toSet
    val expected = Set(
      Vectors.dense(1.0, 2.0, 0.0, 0.0),
      Vectors.dense(0.0, 3.0, 4.0, 0.0),
      Vectors.dense(0.0, 0.0, 5.0, 6.0),
      Vectors.dense(7.0, 0.0, 0.0, 8.0),
      Vectors.dense(0.0, 9.0, 0.0, 0.0))
    assert(rows === expected)
  }

  test("toBlockMatrix") {//分块矩阵
    /**
    * 分块矩阵(BlockMatrix)是由RDD支撑的分布式矩阵,RDD中的元素为MatrixBlock,
    * MatrixBlock是多个((Int, Int),Matrix)组成的元组,其中(Int,Int)是分块索引,Matriax是指定索引处的子矩阵
    */
    val blockMat = mat.toBlockMatrix(2, 2)
    assert(blockMat.numRows() === m)
    assert(blockMat.numCols() === n)
    assert(blockMat.toBreeze() === mat.toBreeze())

    intercept[IllegalArgumentException] {
      mat.toBlockMatrix(-1, 2)
    }
    intercept[IllegalArgumentException] {
      mat.toBlockMatrix(2, 0)
    }
  }
}
