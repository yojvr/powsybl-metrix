# 
# Copyright (c) 2021, RTE (http://www.rte-france.com)
# See AUTHORS.txt
# All rights reserved.
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, you can obtain one at http://mozilla.org/MPL/2.0/.
# SPDX-License-Identifier: MPL-2.0
# 

﻿Même test que émulation AC et TD en curatif, mais on teste le seuil ITAM
Une parade NRF est donc créée mais le curatif de l'émulation AC doit pouvoir agir sur l'incident père (ce qui n'est pas le cas des autres curatifs)

Var  0 : RAS
Var  1 : HVDC en butée en N et N-1 (consigne trop forte)
Var  2 : HVDC en consigne inversée, pas en butée, curatif TD activé mais pas suffisant
Var  3 : HVDC à consigne nulle, curatif TD activé mais pas suffisant
Var  4 : HVDC en butée en N et N-1
Var  5 : HVDC pas en butée, curatif TD activé  
Var  6 : HVDC en butée, curatif TD activé