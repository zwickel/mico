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

import { Component, OnInit, OnDestroy } from '@angular/core';
import { ApiService } from 'src/app/api/api.service';
import { Subscription } from 'rxjs';
import { ApiModel } from 'src/app/api/apimodel';

@Component({
    selector: 'mico-create-service',
    templateUrl: './create-service.component.html',
    styleUrls: ['./create-service.component.css']
})
export class CreateServiceDialogComponent implements OnInit, OnDestroy {

    serviceData;

    // github crawler specific data
    githubData;
    picked = 'latest';
    possibleVersions = [];


    // manual: 0, github: 1
    selectedTab = 0;

    subModelDefinitions: Subscription;
    filterList: string[];

    constructor(private apiService: ApiService) {
        this.subModelDefinitions = this.apiService.getModelDefinitions().subscribe(val => {
            this.filterList = (val['MicoService'] as ApiModel).required.filter((value) => value !== 'serviceInterfaces');
        });
    }

    ngOnInit() {
    }

    ngOnDestroy() {
        if (this.subModelDefinitions != null) {
            this.subModelDefinitions.unsubscribe();
        }
    }

    mapTabIndexToString(index) {
        if (index === 0) {
            return 'manual';
        } else if (index === 1) {
            return 'github';
        } else {
            return 'unknown';
        }
    }

    input() {
        // return information based on selected tab
        if (this.selectedTab === 0) {
            // manual
            return { tab: this.mapTabIndexToString(this.selectedTab), data: this.serviceData };
        } else if (this.selectedTab === 1) {
            // github
            return { tab: this.mapTabIndexToString(this.selectedTab), data: this.githubData };
        } else {
            // error case
            return { tab: this.mapTabIndexToString(this.selectedTab), data: undefined };
        }
    }

    tabChange(event) {
        this.selectedTab = event.index;
    }


    gitStepperChange(event) {

        if (event.selectedIndex === 1 && event.previouslySelectedIndex === 0) {
            console.log('load versions');

        }
    }
}
