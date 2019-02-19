/*
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

package io.github.ust.mico.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.github.ust.mico.core.configuration.CorsConfig;
import io.github.ust.mico.core.configuration.MicoKubernetesConfig;
import io.github.ust.mico.core.configuration.PrometheusConfig;
import io.github.ust.mico.core.dto.PrometheusResponse;
import io.github.ust.mico.core.model.MicoApplication;
import io.github.ust.mico.core.model.MicoService;
import io.github.ust.mico.core.model.MicoServiceInterface;
import io.github.ust.mico.core.persistence.MicoApplicationRepository;
import io.github.ust.mico.core.persistence.MicoServiceRepository;
import io.github.ust.mico.core.service.MicoKubernetesClient;
import io.github.ust.mico.core.util.CollectionUtils;
import io.github.ust.mico.core.web.ApplicationController;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.github.ust.mico.core.JsonPathBuilder.*;
import static io.github.ust.mico.core.TestConstants.SHORT_NAME;
import static io.github.ust.mico.core.TestConstants.VERSION;
import static io.github.ust.mico.core.TestConstants.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@WebMvcTest(ApplicationController.class)
@OverrideAutoConfiguration(enabled = true) //Needed to override our neo4j config
@EnableAutoConfiguration
@EnableConfigurationProperties(value = {CorsConfig.class})
@SuppressWarnings({"rawtypes", "unchecked"})
public class ApplicationControllerTests {

    private static final String JSON_PATH_LINKS_SECTION = "$._links.";

    public static final String APPLICATION_LIST = buildPath(EMBEDDED, "micoApplicationList");
    public static final String SHORT_NAME_PATH = buildPath(ROOT, "shortName");
    public static final String VERSION_PATH = buildPath(ROOT, "version");
    public static final String DESCRIPTION_PATH = buildPath(ROOT, "description");
    public static final String SERVICES_LIST_PATH = buildPath(ROOT, "services");
    public static final String INTERFACES_LIST_PATH = buildPath(ROOT, "serviceInterfaces");
    public static final String ID_PATH = buildPath(ROOT, "id");

    private static final String FIRST_SERVICE = buildPath(ROOT, "serviceDeploymentInformation[0]");
    private static final String REQUESTED_REPLICAS = buildPath(FIRST_SERVICE, "requestedReplicas");
    private static final String AVAILABLE_REPLICAS = buildPath(FIRST_SERVICE, "availableReplicas");
    private static final String INTERFACES_INFORMATION = buildPath(FIRST_SERVICE, "interfacesInformation");
    private static final String INTERFACES_INFORMATION_NAME = buildPath(FIRST_SERVICE, "interfacesInformation[0].name");
    private static final String POD_INFO = buildPath(FIRST_SERVICE, "podInfo");
    private static final String POD_INFO_POD_NAME = buildPath(FIRST_SERVICE, "podInfo[0].podName");
    private static final String POD_INFO_PHASE = buildPath(FIRST_SERVICE, "podInfo[0].phase");
    private static final String POD_INFO_NODE_NAME = buildPath(FIRST_SERVICE, "podInfo[0].nodeName");
    private static final String POD_INFO_METRICS_MEMORY_USAGE = buildPath(FIRST_SERVICE, "podInfo[0].metrics.memoryUsage");
    private static final String POD_INFO_METRICS_CPU_LOAD = buildPath(FIRST_SERVICE, "podInfo[0].metrics.cpuLoad");
    private static final String POD_INFO_METRICS_AVAILABLE = buildPath(FIRST_SERVICE, "podInfo[0].metrics.available");

    public static final String VERSION_MAJOR_PATH = buildPath(ROOT, "version", "majorVersion");
    public static final String VERSION_MINOR_PATH = buildPath(ROOT, "version", "minorVersion");
    public static final String VERSION_PATCH_PATH = buildPath(ROOT, "version", "patchVersion");

    private static final String BASE_PATH = "/applications";

    @MockBean
    private PrometheusConfig prometheusConfig;

    @MockBean
    private MicoKubernetesConfig micoKubernetesConfig;

    @MockBean
    private MicoApplicationRepository applicationRepository;

    @MockBean
    private MicoServiceRepository serviceRepository;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private MicoKubernetesClient micoKubernetesClient;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    CorsConfig corsConfig;

    @Test
    public void getAllApplications() throws Exception {
        given(applicationRepository.findAll(3)).willReturn(
            Arrays.asList(new MicoApplication().setShortName(SHORT_NAME).setVersion(VERSION_1_0_1),
                new MicoApplication().setShortName(SHORT_NAME).setVersion(VERSION),
                new MicoApplication().setShortName(SHORT_NAME_1).setVersion(VERSION)));

        mvc.perform(get("/applications").accept(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaTypes.HAL_JSON_UTF8_VALUE))
            .andExpect(jsonPath(APPLICATION_LIST + "[*]", hasSize(3)))
            .andExpect(jsonPath(APPLICATION_LIST + "[?(" + SHORT_NAME_MATCHER + "&& @.version=='" + VERSION_1_0_1 + "')]", hasSize(1)))
            .andExpect(jsonPath(APPLICATION_LIST + "[?(" + SHORT_NAME_MATCHER + "&& @.version=='" + VERSION + "')]", hasSize(1)))
            .andExpect(jsonPath(APPLICATION_LIST + "[?(" + SHORT_NAME_1_MATCHER + "&& @.version=='" + VERSION + "')]", hasSize(1)))
            .andExpect(jsonPath(JSON_PATH_LINKS_SECTION + "self.href", is("http://localhost/applications")))
            .andReturn();
    }

    @Test
    public void getApplicationByShortNameAndVersion() throws Exception {
        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(
            Optional.of(new MicoApplication().setShortName(SHORT_NAME).setVersion(VERSION)));

        mvc.perform(get("/applications/" + SHORT_NAME + "/" + VERSION).accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaTypes.HAL_JSON_UTF8_VALUE))
            .andExpect(jsonPath(SHORT_NAME_PATH, is(SHORT_NAME)))
            .andExpect(jsonPath(VERSION_PATH, is(VERSION)))
            .andExpect(jsonPath(JSON_PATH_LINKS_SECTION + SELF_HREF, is("http://localhost/applications/" + SHORT_NAME + "/" + VERSION)))
            .andExpect(jsonPath(JSON_PATH_LINKS_SECTION + "applications.href", is("http://localhost/applications")))
            .andReturn();
    }

    @Test
    public void getApplicationByShortName() throws Exception {
        given(applicationRepository.findByShortName(SHORT_NAME)).willReturn(CollectionUtils.listOf(new MicoApplication().setShortName(SHORT_NAME).setVersion(VERSION)));

        mvc.perform(get("/applications/" + SHORT_NAME + "/").accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaTypes.HAL_JSON_UTF8_VALUE))
            .andExpect(jsonPath(APPLICATION_LIST + "[?(" + SHORT_NAME_MATCHER + "&& @.version=='" + VERSION + "')]", hasSize(1)))
            .andExpect(jsonPath(JSON_PATH_LINKS_SECTION + SELF_HREF, is("http://localhost/applications/" + SHORT_NAME)))
            .andReturn();
    }

    @Test
    public void getApplicationByShortNameWithTrailingSlash() throws Exception {
        given(applicationRepository.findByShortName(SHORT_NAME)).willReturn(CollectionUtils.listOf(new MicoApplication().setShortName(SHORT_NAME).setVersion(VERSION)));

        mvc.perform(get("/applications/" + SHORT_NAME + "/").accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    public void createApplicationWithoutServices() throws Exception {
        MicoApplication application = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription(DESCRIPTION);

        given(applicationRepository.save(any(MicoApplication.class))).willReturn(application);
        given(serviceRepository.findByShortNameAndVersion(
            anyString(), anyString()))
            .willReturn(Optional.empty());

        prettyPrint(application);

        final ResultActions result = mvc.perform(post(BASE_PATH)
            .content(mapper.writeValueAsBytes(application))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(jsonPath(SHORT_NAME_PATH, is(application.getShortName())))
            .andExpect(jsonPath(VERSION_PATH, is(application.getVersion())))
            .andExpect(jsonPath(SERVICES_LIST_PATH, IsEmptyCollection.empty()));

        result.andExpect(status().isCreated());
    }

    @Test
    public void createApplicationWithExistingServices() throws Exception {
        List<MicoService> micoServices = CollectionUtils.listOf(
            new MicoService()
                .setShortName(SHORT_NAME)
                .setVersion(VERSION),
            new MicoService()
                .setShortName(SHORT_NAME_1)
                .setVersion(VERSION)
        );

        MicoApplication application = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription(DESCRIPTION)
            .setServices(micoServices);

        given(applicationRepository.save(any(MicoApplication.class))).willReturn(application);
        given(serviceRepository.findByShortNameAndVersion(
            eq(micoServices.get(0).getShortName()), eq(micoServices.get(0).getVersion())))
            .willReturn(Optional.of(micoServices.get(0)));
        given(serviceRepository.findByShortNameAndVersion(
            eq(micoServices.get(1).getShortName()), eq(micoServices.get(1).getVersion())))
            .willReturn(Optional.of(micoServices.get(1)));

        prettyPrint(application);

        final ResultActions result = mvc.perform(post(BASE_PATH)
            .content(mapper.writeValueAsBytes(application))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(jsonPath(SHORT_NAME_PATH, is(application.getShortName())))
            .andExpect(jsonPath(VERSION_PATH, is(application.getVersion())))
            .andExpect(jsonPath(SERVICES_LIST_PATH + "[*]", hasSize(2)));

        result.andExpect(status().isCreated());
    }

    @Test
    public void createApplicationWithNotExistingServices() throws Exception {
        List<MicoService> micoServices = CollectionUtils.listOf(
            new MicoService()
                .setShortName(SHORT_NAME)
                .setVersion(VERSION),
            new MicoService()
                .setShortName(SHORT_NAME_1)
                .setVersion(VERSION)
        );

        MicoApplication application = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription(DESCRIPTION)
            .setServices(micoServices);

        // Only one of the two MicoService exists -> exception
        given(serviceRepository.findByShortNameAndVersion(
            eq(micoServices.get(0).getShortName()), eq(micoServices.get(0).getVersion())))
            .willReturn(Optional.of(micoServices.get(0)));

        prettyPrint(application);

        final ResultActions result = mvc.perform(post(BASE_PATH)
            .content(mapper.writeValueAsBytes(application))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print());

        result.andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createApplicationWithInconsistentServiceData() throws Exception {
        MicoService existingMicoService = new MicoService()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setGitCloneUrl(GIT_TEST_REPO_URL);
        MicoService invalidMicoService = new MicoService()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setGitCloneUrl("http://example.com/INVALID");

        MicoApplication application = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription(DESCRIPTION)
            .setServices(CollectionUtils.listOf(
                invalidMicoService
            ));

        // MicoService exist with different data
        given(serviceRepository.findByShortNameAndVersion(
            eq(invalidMicoService.getShortName()), eq(invalidMicoService.getVersion())))
            .willReturn(Optional.of(existingMicoService));

        prettyPrint(application);

        final ResultActions result = mvc.perform(post(BASE_PATH)
            .content(mapper.writeValueAsBytes(application))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print());

        result.andExpect(status().isConflict());
    }

    @Test
    public void updateApplication() throws Exception {
        MicoApplication existingApplication = new MicoApplication()
            .setId(ID)
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription(DESCRIPTION);

        MicoApplication updatedApplication = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription("newDesc");

        MicoApplication expectedApplication = new MicoApplication()
            .setId(existingApplication.getId())
            .setShortName(updatedApplication.getShortName())
            .setVersion(updatedApplication.getVersion())
            .setDescription(updatedApplication.getDescription())
            .setServices(existingApplication.getServices());

        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(existingApplication));
        given(applicationRepository.save(eq(expectedApplication))).willReturn(expectedApplication);
        //given(applicationRepository.save(any(MicoApplication.class))).willThrow(new RuntimeException("Unexpected MicoApplication"));

        ResultActions resultUpdate = mvc.perform(put(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION)
            .content(mapper.writeValueAsBytes(updatedApplication))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(jsonPath(ID_PATH, is(existingApplication.getId().intValue())))
            .andExpect(jsonPath(DESCRIPTION_PATH, is(updatedApplication.getDescription())))
            .andExpect(jsonPath(SHORT_NAME_PATH, is(updatedApplication.getShortName())))
            .andExpect(jsonPath(VERSION_PATH, is(updatedApplication.getVersion())))
            .andExpect(jsonPath(SERVICES_LIST_PATH + "[*]", IsEmptyCollection.empty()));

        resultUpdate.andExpect(status().isOk());
    }

    @Test
    public void updateApplicationUsesExistingServices() throws Exception {
        MicoService existingMicoService = new MicoService()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setGitCloneUrl(GIT_TEST_REPO_URL);

        MicoApplication existingApplication = new MicoApplication()
            .setId(ID)
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription(DESCRIPTION)
            .setServices(CollectionUtils.listOf(existingMicoService));

        MicoApplication updatedApplication = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription("newDesc");

        MicoApplication expectedApplication = new MicoApplication()
            .setId(existingApplication.getId())
            .setShortName(updatedApplication.getShortName())
            .setVersion(updatedApplication.getVersion())
            .setDescription(updatedApplication.getDescription())
            .setServices(existingApplication.getServices());

        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(existingApplication));
        given(applicationRepository.save(eq(expectedApplication))).willReturn(expectedApplication);

        ResultActions resultUpdate = mvc.perform(put(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION)
            .content(mapper.writeValueAsBytes(updatedApplication))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(jsonPath(ID_PATH, is(existingApplication.getId().intValue())))
            .andExpect(jsonPath(DESCRIPTION_PATH, is(updatedApplication.getDescription())))
            .andExpect(jsonPath(SHORT_NAME_PATH, is(updatedApplication.getShortName())))
            .andExpect(jsonPath(VERSION_PATH, is(updatedApplication.getVersion())))
            .andExpect(jsonPath(SERVICES_LIST_PATH + "[*]", hasSize(1)));

        resultUpdate.andExpect(status().isOk());
    }

    @Test
    public void updateApplicationIsOnlyAllowedWithoutServices() throws Exception {
        MicoService micoService = new MicoService()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setGitCloneUrl(GIT_TEST_REPO_URL);

        MicoApplication application = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription(DESCRIPTION)
            .setServices(CollectionUtils.listOf(micoService));

        MicoApplication updatedApplication = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setDescription("newDesc")
            .setServices(CollectionUtils.listOf(micoService));

        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(application));

        ResultActions resultUpdate = mvc.perform(put(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION)
            .content(mapper.writeValueAsBytes(updatedApplication))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print());

        resultUpdate.andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void deleteApplication() throws Exception {
        MicoApplication app = new MicoApplication().setShortName(SHORT_NAME).setVersion(VERSION);
        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(app));

        ArgumentCaptor<MicoApplication> appCaptor = ArgumentCaptor.forClass(MicoApplication.class);

        mvc.perform(delete("/applications/" + SHORT_NAME + "/" + VERSION).accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isNoContent())
            .andReturn();

        verify(applicationRepository).delete(appCaptor.capture());
        assertEquals("Wrong short name.", app.getShortName(), appCaptor.getValue().getShortName());
        assertEquals("Wrong version.", app.getVersion(), appCaptor.getValue().getVersion());
    }

    @Test
    public void getStatusOfApplication() throws Exception {
        MicoApplication application = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION)
            .setServices(CollectionUtils.listOf(
                new MicoService()
                    .setShortName(SHORT_NAME)
                    .setVersion(VERSION)
                    .setServiceInterfaces(CollectionUtils.listOf(
                        new MicoServiceInterface()
                            .setServiceInterfaceName(SERVICE_INTERFACE_NAME)
                    ))
            ));
        String testNamespace = "TestNamespace";
        String nodeName = "testNode";
        String podPhase = "Running";
        String hostIp = "192.168.0.0";
        String deploymentName = "deployment1";
        String serviceName = "service1";
        String podName = "pod1";
        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(application));
        given(micoKubernetesConfig.getNamespaceMicoWorkspace()).willReturn(testNamespace);

        final int availableReplicas = 1;
        final int replicas = 1;

        Optional<Deployment> deployment = Optional.of(new DeploymentBuilder()
            .withNewMetadata().withName(deploymentName).endMetadata()
            .withNewSpec().withReplicas(replicas).endSpec().withNewStatus().withAvailableReplicas(availableReplicas).endStatus()
            .build());
        Optional<Service> kubernetesService = Optional.of(new ServiceBuilder()
            .withNewMetadata().withName(serviceName).endMetadata()
            .build());
        PodList podList = new PodListBuilder()
            .addNewItem()
            .withNewMetadata().withName(podName).endMetadata()
            .withNewSpec().withNodeName(nodeName).endSpec()
            .withNewStatus().withPhase(podPhase).withHostIP(hostIp).endStatus()
            .endItem()
            .build();
        given(micoKubernetesClient.getDeploymentOfMicoService(any(MicoService.class))).willReturn(deployment);
        given(micoKubernetesClient.getPodsCreatedByDeploymentOfMicoService(any(MicoService.class))).willReturn(podList.getItems());
        given(micoKubernetesClient.getInterfaceByNameOfMicoService(any(MicoService.class), anyString())).willReturn(kubernetesService);

        given(prometheusConfig.getUri()).willReturn("http://localhost:9090/api/v1/query");

        int memoryUsage = 1;
        ResponseEntity responseEntity = getPrometheusResponseEntity(memoryUsage);
        int cpuLoad = 2;
        ResponseEntity responseEntity2 = getPrometheusResponseEntity(cpuLoad);
        given(restTemplate.getForEntity(any(), eq(PrometheusResponse.class))).willReturn(responseEntity).willReturn(responseEntity2);

        mvc.perform(get(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION + "/status"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath(REQUESTED_REPLICAS, is(replicas)))
            .andExpect(jsonPath(AVAILABLE_REPLICAS, is(availableReplicas)))
            .andExpect(jsonPath(INTERFACES_INFORMATION, hasSize(1)))
            .andExpect(jsonPath(INTERFACES_INFORMATION_NAME, is(serviceName)))
            .andExpect(jsonPath(POD_INFO, hasSize(1)))
            .andExpect(jsonPath(POD_INFO_POD_NAME, is(podName)))
            .andExpect(jsonPath(POD_INFO_PHASE, is(podPhase)))
            .andExpect(jsonPath(POD_INFO_NODE_NAME, is(nodeName)))
            .andExpect(jsonPath(POD_INFO_METRICS_MEMORY_USAGE, is(memoryUsage)))
            .andExpect(jsonPath(POD_INFO_METRICS_CPU_LOAD, is(cpuLoad)))
            .andExpect(jsonPath(POD_INFO_METRICS_AVAILABLE, is(true)));
    }

    @Test
    public void addServiceToApplication() throws Exception {
        MicoApplication micoApplication = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION);
        MicoService micoService = new MicoService()
            .setShortName(SERVICE_SHORT_NAME)
            .setVersion(SERVICE_VERSION);

        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(micoApplication));
        given(serviceRepository.findByShortNameAndVersion(SERVICE_SHORT_NAME, SERVICE_VERSION)).willReturn(Optional.of(micoService));
        ArgumentCaptor<MicoApplication> micoApplicationCaptor = ArgumentCaptor.forClass(MicoApplication.class);

        mvc.perform(post(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION + "/services")
            .content(mapper.writeValueAsBytes(micoService))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(status().isNoContent());

        verify(applicationRepository, times(1)).save(micoApplicationCaptor.capture());
        MicoApplication savedMicoApplication = micoApplicationCaptor.getValue();
        assertEquals("Expected one service", savedMicoApplication.getServices().size(), 1);
        assertEquals(savedMicoApplication.getServices().get(0), micoService);
    }

    @Test
    public void addServiceOnlyWithNameAndVersionToApplication() throws Exception {
        MicoApplication micoApplication = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION);
        MicoService existingService = new MicoService()
            .setShortName(SERVICE_SHORT_NAME)
            .setVersion(SERVICE_VERSION)
            .setDescription(DESCRIPTION)
            .setGitCloneUrl(GIT_TEST_REPO_URL);
        MicoService providedService = new MicoService()
            .setShortName(SERVICE_SHORT_NAME)
            .setVersion(SERVICE_VERSION);

        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(micoApplication));
        given(serviceRepository.findByShortNameAndVersion(SERVICE_SHORT_NAME, SERVICE_VERSION)).willReturn(Optional.of(existingService));
        ArgumentCaptor<MicoApplication> micoApplicationCaptor = ArgumentCaptor.forClass(MicoApplication.class);

        mvc.perform(post(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION + "/services")
            .content(mapper.writeValueAsBytes(providedService))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(status().isNoContent());

        verify(applicationRepository, times(1)).save(micoApplicationCaptor.capture());
        MicoApplication savedMicoApplication = micoApplicationCaptor.getValue();
        assertEquals("Expected one service", savedMicoApplication.getServices().size(), 1);
        assertEquals(savedMicoApplication.getServices().get(0), existingService);
    }

    @Test
    public void addServiceWithInconsistentDataToApplication() throws Exception {
        MicoApplication micoApplication = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION);
        MicoService existingService = new MicoService()
            .setShortName(SERVICE_SHORT_NAME)
            .setVersion(SERVICE_VERSION)
            .setDescription(DESCRIPTION);
        MicoService providedService = new MicoService()
            .setShortName(SERVICE_SHORT_NAME)
            .setVersion(SERVICE_VERSION)
            .setDescription("NewDesc");

        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(micoApplication));
        given(serviceRepository.findByShortNameAndVersion(SERVICE_SHORT_NAME, SERVICE_VERSION)).willReturn(Optional.of(existingService));

        mvc.perform(post(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION + "/services")
            .content(mapper.writeValueAsBytes(providedService))
            .contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(status().isConflict());
    }

    @Test
    public void deleteServiceFromApplication() throws Exception {
        MicoApplication micoApplication = new MicoApplication()
            .setShortName(SHORT_NAME)
            .setVersion(VERSION);
        MicoService micoService = new MicoService().setShortName(SERVICE_SHORT_NAME).setVersion(SERVICE_VERSION);
        micoApplication.getServices().add(micoService);
        given(applicationRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(Optional.of(micoApplication));
        ArgumentCaptor<MicoApplication> micoApplicationCaptor = ArgumentCaptor.forClass(MicoApplication.class);

        mvc.perform(delete(BASE_PATH + "/" + SHORT_NAME + "/" + VERSION + "/services/" + SERVICE_SHORT_NAME))
            .andDo(print())
            .andExpect(status().isNoContent());
        verify(applicationRepository, times(1)).save(micoApplicationCaptor.capture());
        MicoApplication savedMicoApplication = micoApplicationCaptor.getValue();
        assertTrue("Expected services are empty", savedMicoApplication.getServices().isEmpty());
    }

    private ResponseEntity getPrometheusResponseEntity(int value) {
        PrometheusResponse prometheusResponse = new PrometheusResponse();
        prometheusResponse.setStatus(PrometheusResponse.PROMETHEUS_SUCCESSFUL_RESPONSE);
        prometheusResponse.setValue(value);
        ResponseEntity responseEntity = mock(ResponseEntity.class);
        given(responseEntity.getStatusCode()).willReturn(HttpStatus.OK);
        given(responseEntity.getBody()).willReturn(prometheusResponse);
        return responseEntity;
    }

    private void prettyPrint(Object object) {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            String json = mapper.writeValueAsString(object);
            System.out.println(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

}