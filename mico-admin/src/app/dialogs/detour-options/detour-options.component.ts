/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { Component, OnInit, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatTableDataSource } from '@angular/material';
import { incrementVersion, versionComponents, versionComparator } from 'src/app/api/semantic-version';
import { ApiObject } from 'src/app/api/apiobject';
import { ApiService } from 'src/app/api/api.service';
import { take } from 'rxjs/operators';
import { SelectionModel } from '@angular/cdk/collections';
import { selection } from 'd3';

@Component({
    selector: 'mico-detour-options',
    templateUrl: './detour-options.component.html',
    styleUrls: ['./detour-options.component.css']
})
export class DetourOptionsComponent implements OnInit {

    constructor(
        public dialogRef: MatDialogRef<DetourOptionsComponent>,
        @Inject(MAT_DIALOG_DATA) public data: any,
        private apiService: ApiService,
    ) {

    }

    ngOnInit() { }

    detourOn() { this.apiService.switchDetour("on"); }
    detourOff() { this.apiService.switchDetour("off"); }

    confirm() {
        return;
    }

}
