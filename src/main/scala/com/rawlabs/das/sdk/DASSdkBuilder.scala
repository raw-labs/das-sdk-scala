/**
 * Copyright 2024 RAW Labs S.A.
 * All rights reserved.
 *
 * This source code is the property of RAW Labs S.A. It contains
 * proprietary and confidential information that is protected by applicable
 * intellectual property and other laws. Unauthorized use, reproduction,
 * or distribution of this code, or any portion of it, may result in severe
 * civil and criminal penalties and will be prosecuted to the maximum
 * extent possible under the law.
 */

package com.rawlabs.das.sdk

import com.rawlabs.utils.core.RawSettings

trait DASSdkBuilder {

  def dasType: String

  def build(options: Map[String, String])(implicit settings: RawSettings): DASSdk

}
