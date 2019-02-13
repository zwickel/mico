package io.github.ust.mico.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.github.ust.mico.core.configuration.CorsConfig;
import io.github.ust.mico.core.configuration.MicoKubernetesConfig;
import io.github.ust.mico.core.service.ClusterAwarenessFabric8;
import io.github.ust.mico.core.web.ServiceInterfaceController;
import io.github.ust.mico.core.model.MicoPortType;
import io.github.ust.mico.core.model.MicoService;
import io.github.ust.mico.core.model.MicoServiceInterface;
import io.github.ust.mico.core.model.MicoServicePort;
import io.github.ust.mico.core.persistence.MicoServiceRepository;
import io.github.ust.mico.core.util.CollectionUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

import static io.github.ust.mico.core.JsonPathBuilder.*;
import static io.github.ust.mico.core.TestConstants.SHORT_NAME;
import static io.github.ust.mico.core.TestConstants.VERSION;
import static io.github.ust.mico.core.service.MicoKubernetesClient.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@WebMvcTest(ServiceInterfaceController.class)
@OverrideAutoConfiguration(enabled = true) //Needed to override our neo4j config
@EnableAutoConfiguration
@EnableConfigurationProperties(value = {CorsConfig.class})
public class ServiceInterfaceControllerTests {

    private static final String JSON_PATH_LINKS_SECTION = buildPath(ROOT, LINKS);
    private static final String SELF_HREF = buildPath(JSON_PATH_LINKS_SECTION, SELF, HREF);
    private static final String INTERFACES_HREF = buildPath(JSON_PATH_LINKS_SECTION, "interfaces", HREF);
    private static final String INTERFACE_NAME = "interface-name";
    private static final int INTERFACE_PORT = 1024;
    private static final MicoPortType INTERFACE_PORT_TYPE = MicoPortType.TCP;
    private static final int INTERFACE_TARGET_PORT = 1025;
    private static final String INTERFACE_DESCRIPTION = "This is a service interface.";
    private static final String INTERFACE_PUBLIC_DNS = "DNS";
    private static final String SERVICES_HREF = buildPath(ROOT, LINKS, "service", HREF);
    private static final String SERVICE_URL = "/services/" + SHORT_NAME + "/" + VERSION;
    private static final String INTERFACES_URL = SERVICE_URL + "/interfaces";
    private static final String PATH_PART_PUBLIC_IP = "publicIP";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MicoServiceRepository serviceRepository;

    @MockBean
    private ClusterAwarenessFabric8 cluster;

    @MockBean
    private MicoKubernetesConfig kubernetesConfig;

    @Autowired
    private ObjectMapper mapper;

    @Test
    public void postServiceInterface() throws Exception {
        given(serviceRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(
            Optional.of(new MicoService().setShortName(SHORT_NAME).setVersion(VERSION))
        );

        MicoServiceInterface serviceInterface = getTestServiceInterface();
        mvc.perform(post(INTERFACES_URL)
            .content(mapper.writeValueAsBytes(serviceInterface)).accept(MediaTypes.HAL_JSON_VALUE).contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(getServiceInterfaceMatcher(serviceInterface, INTERFACES_URL, SERVICE_URL))
            .andReturn();
    }

    @Test
    public void postServiceNotFound() throws Exception {
        given(serviceRepository.findByShortNameAndVersion(any(), any())).willReturn(
            Optional.empty()
        );
        MicoServiceInterface serviceInterface = getTestServiceInterface();
        mvc.perform(post(INTERFACES_URL)
            .content(mapper.writeValueAsBytes(serviceInterface)).accept(MediaTypes.HAL_JSON_VALUE).contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andReturn();
    }

    @Test
    public void postServiceInterfaceExists() throws Exception {
        MicoServiceInterface serviceInterface = getTestServiceInterface();
        MicoService service = new MicoService().setShortName(SHORT_NAME).setVersion(VERSION);
        service.getServiceInterfaces().add(serviceInterface);
        given(serviceRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(
            Optional.of(service)
        );
        mvc.perform(post(INTERFACES_URL)
            .content(mapper.writeValueAsBytes(serviceInterface)).accept(MediaTypes.HAL_JSON_VALUE).contentType(MediaTypes.HAL_JSON_UTF8_VALUE))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(status().reason("An interface with this name is already associated with this service."))
            .andReturn();
    }

    @Test
    public void getSpecificServiceInterface() throws Exception {
        MicoServiceInterface serviceInterface = getTestServiceInterface();
        given(serviceRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(
            Optional.of(new MicoService().setServiceInterfaces(CollectionUtils.listOf(serviceInterface))));

        mvc.perform(get(INTERFACES_URL + "/" + serviceInterface.getServiceInterfaceName()).accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(getServiceInterfaceMatcher(serviceInterface, INTERFACES_URL, SERVICE_URL))
            .andReturn();

    }

    @Test
    public void getSpecificServiceInterfaceNotFound() throws Exception {
        given(serviceRepository.findInterfaceOfServiceByName(any(), any(), any())).willReturn(
            Optional.empty());

        mvc.perform(get(INTERFACES_URL + "/NotThereInterface").accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andReturn();

    }

    @Test
    public void getAllServiceInterfacesOfService() throws Exception {
        MicoServiceInterface serviceInterface0 = new MicoServiceInterface().setServiceInterfaceName("ServiceInterface0");
        MicoServiceInterface serviceInterface1 = new MicoServiceInterface().setServiceInterfaceName("ServiceInterface1");
        List<MicoServiceInterface> serviceInterfaces = Arrays.asList(serviceInterface0, serviceInterface1);
        given(serviceRepository.findByShortNameAndVersion(SHORT_NAME, VERSION)).willReturn(
            Optional.of(new MicoService().setServiceInterfaces(serviceInterfaces)));
        mvc.perform(get(INTERFACES_URL).accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.micoServiceInterfaceList[*]", hasSize(serviceInterfaces.size())))
            .andExpect(jsonPath("$._embedded.micoServiceInterfaceList[?(@.serviceInterfaceName =='" + serviceInterface0.getServiceInterfaceName() + "')]", hasSize(1)))
            .andExpect(jsonPath("$._embedded.micoServiceInterfaceList[?(@.serviceInterfaceName =='" + serviceInterface1.getServiceInterfaceName() + "')]", hasSize(1)))
            .andReturn();
    }

    @Test
    public void getInterfacePublicIpByName() throws Exception {

        List<String> externalIPs = new ArrayList<>();
        externalIPs.add("1.2.3.4");
        MicoServiceInterface serviceInterface = getTestServiceInterface();
        String serviceInterfaceName = serviceInterface.getServiceInterfaceName();
        given(serviceRepository.findInterfaceOfServiceByName(serviceInterfaceName, SHORT_NAME, VERSION)).willReturn(
            Optional.of(serviceInterface));

        Service service = getKubernetesService(serviceInterfaceName, externalIPs);
        ServiceList serviceList = new ServiceList();
        serviceList.setItems(CollectionUtils.listOf(service));
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL_APP_KEY, SHORT_NAME);
        labels.put(LABEL_VERSION_KEY, VERSION);
        labels.put(LABEL_INTERFACE_KEY, serviceInterfaceName);
        String namespace = kubernetesConfig.getNamespaceMicoWorkspace();
        given(cluster.getServicesByLabels(labels, namespace)).willReturn(serviceList);

        mvc.perform(get(INTERFACES_URL + "/" + serviceInterfaceName + "/" + PATH_PART_PUBLIC_IP).accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$", is(externalIPs)))
            .andReturn();
    }

    @Test
    public void getInterfacePublicIpByNameWithPendingIP() throws Exception {

        List<String> externalIPs = new ArrayList<>();
        MicoServiceInterface serviceInterface = getTestServiceInterface();
        String serviceInterfaceName = serviceInterface.getServiceInterfaceName();
        given(serviceRepository.findInterfaceOfServiceByName(serviceInterfaceName, SHORT_NAME, VERSION)).willReturn(
            Optional.of(serviceInterface));
        Service service = getKubernetesService(serviceInterfaceName, externalIPs);
        ServiceList serviceList = new ServiceList();
        serviceList.setItems(CollectionUtils.listOf(service));
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL_APP_KEY, SHORT_NAME);
        labels.put(LABEL_VERSION_KEY, VERSION);
        labels.put(LABEL_INTERFACE_KEY, serviceInterfaceName);
        String namespace = kubernetesConfig.getNamespaceMicoWorkspace();
        given(cluster.getServicesByLabels(labels, namespace)).willReturn(serviceList);

        mvc.perform(get(INTERFACES_URL + "/" + serviceInterfaceName + "/" + PATH_PART_PUBLIC_IP).accept(MediaTypes.HAL_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().json("[]"))
            .andReturn();
    }

    private Service getKubernetesService(String serviceInterfaceName, List<String> externalIPs) {
        Service service = new ServiceBuilder()
            .withNewMetadata()
            .withName(serviceInterfaceName)
            .endMetadata()
            .withNewStatus()
            .withNewLoadBalancer()
            .endLoadBalancer()
            .endStatus()
            .build();

        if (externalIPs != null && !externalIPs.isEmpty()) {
            List<LoadBalancerIngress> ingressList = new ArrayList<LoadBalancerIngress>();
            for (String externalIP : externalIPs) {
                LoadBalancerIngress ingress = new LoadBalancerIngress();
                ingress.setIp(externalIP);
                ingressList.add(ingress);
            }
            service.getStatus().getLoadBalancer().setIngress(ingressList);
        }
        return service;
    }

    private MicoServiceInterface getTestServiceInterface() {
        return new MicoServiceInterface()
            .setServiceInterfaceName(INTERFACE_NAME)
            .setPorts(CollectionUtils.listOf(new MicoServicePort()
                .setNumber(INTERFACE_PORT)
                .setType(INTERFACE_PORT_TYPE)
                .setTargetPort(INTERFACE_TARGET_PORT)))
            .setDescription(INTERFACE_DESCRIPTION)
            .setPublicDns(INTERFACE_PUBLIC_DNS);
    }

    private ResultMatcher getServiceInterfaceMatcher(MicoServiceInterface serviceInterface, String selfBaseUrl, String serviceUrl) {
        URI selfHrefEnding = UriComponentsBuilder.fromUriString(selfBaseUrl + "/" + serviceInterface.getServiceInterfaceName()).build().encode().toUri();
        return ResultMatcher.matchAll(header().string(HttpHeaders.CONTENT_TYPE, MediaTypes.HAL_JSON_UTF8_VALUE),
            jsonPath("$.serviceInterfaceName", is(serviceInterface.getServiceInterfaceName())),
            jsonPath("$.ports", hasSize(serviceInterface.getPorts().size())),
            jsonPath("$.ports", not(empty())),
            jsonPath("$.protocol", is(serviceInterface.getProtocol())),
            jsonPath("$.description", is(serviceInterface.getDescription())),
            jsonPath("$.publicDns", is(serviceInterface.getPublicDns())),
            jsonPath("$.transportProtocol", is(serviceInterface.getTransportProtocol())),
            jsonPath(SELF_HREF, endsWith(selfHrefEnding.toString())),
            jsonPath(INTERFACES_HREF, endsWith(selfBaseUrl)),
            jsonPath(SERVICES_HREF, endsWith(serviceUrl)));
    }
}
