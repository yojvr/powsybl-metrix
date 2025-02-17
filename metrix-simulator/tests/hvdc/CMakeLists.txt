# 
# Copyright (c) 2021, RTE (http://www.rte-france.com)
# See AUTHORS.txt
# All rights reserved.
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, you can obtain one at http://mozilla.org/MPL/2.0/.
# SPDX-License-Identifier: MPL-2.0
# 

MESSAGE("Remedial action tests...")

set(TEST_DIR_NAME hvdc)
set(TEST_DIR ${MAIN_TEST_DIR}/${TEST_DIR_NAME})
set(EXPECTED_TEST_DIR ${MAIN_TEST_DIR}_reference/${TEST_DIR_NAME})

metrix_test("hvdcEmulAC" 8)
metrix_test("hvdcEmulAC_k5000" 8)
metrix_test("hvdcWatchSection" 1)
metrix_test("hvdcEmulAC_curatif" 7)
metrix_test("hvdcEmulAC_curatifTD" 7)
metrix_test("hvdcEmulAC_et_seuil_ITAM" 7)
metrix_test("penalisation_HVDC" 8)
metrix_test("penalisation_HVDC_2_curatif" 8)
metrix_test("incidentHvdc" 3)
metrix_test("incidentHvdcEmulAC" 3)
metrix_test("incidentHvdc_non_connexite" 2)
metrix_test("incidentHvdcEmulAC_non_connexite" 2)
metrix_test("incidentHvdcCuratif" 2)
