package ust.tad.kubernetesplugin.analysis;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ust.tad.kubernetesplugin.kubernetesmodel.deployment.Container;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.ContainerPort;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.EnvironmentVariable;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.KubernetesDeployment;
import ust.tad.kubernetesplugin.kubernetesmodel.service.KubernetesService;
import ust.tad.kubernetesplugin.kubernetesmodel.service.Selector;
import ust.tad.kubernetesplugin.kubernetesmodel.service.ServicePort;
import ust.tad.kubernetesplugin.models.tadm.Artifact;
import ust.tad.kubernetesplugin.models.tadm.Component;
import ust.tad.kubernetesplugin.models.tadm.ComponentType;
import ust.tad.kubernetesplugin.models.tadm.Confidence;
import ust.tad.kubernetesplugin.models.tadm.InvalidPropertyValueException;
import ust.tad.kubernetesplugin.models.tadm.InvalidRelationException;
import ust.tad.kubernetesplugin.models.tadm.Property;
import ust.tad.kubernetesplugin.models.tadm.PropertyType;
import ust.tad.kubernetesplugin.models.tadm.TechnologyAgnosticDeploymentModel;

@Service
public class TransformationService {

    @Autowired
    private RelationFinderService relationFinderService;

    private Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments = new HashMap<>();

    /**
     * Creates EDMM components, component types and relations from the given deployments and services 
     * of the internal Kubernetes model.
     * Adds them to the given technology-agnostic deployment model.
     * 
     * @param tadm
     * @param deployments
     * @param services
     * @return the modified technology-agnostic deployment model.
     * @throws InvalidPropertyValueException
     * @throws InvalidRelationException
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public TechnologyAgnosticDeploymentModel transformInternalToTADM(
        TechnologyAgnosticDeploymentModel tadm, 
        Set<KubernetesDeployment> deployments,  
        Set<KubernetesService> services) throws InvalidPropertyValueException, InvalidRelationException, URISyntaxException {
            List<Component> newComponents = new ArrayList<>();
            List<ComponentType> newComponentTypes = new ArrayList<>();
            for (KubernetesDeployment deployment : deployments) {
                Component component = new Component();
                component.setConfidence(Confidence.CONFIRMED);
                component.setName(deployment.getName());

                for (Container container : deployment.getContainer()) {
                    List<Artifact> artifacts = component.getArtifacts();
                    artifacts.add(createArtifactFromImage(container.getImage()));
                    component.setArtifacts(artifacts);

                    List<Property> properties = component.getProperties();
                    properties.addAll(createPropertiesForContainerPorts(container.getContainerPorts()));
                    properties.addAll(createPropertiesForEnvVariables(container.getEnvironmentVariables()));
                    component.setProperties(properties);
                }
                
                List<Property> properties = component.getProperties();
                Set<ContainerPort> containerPorts = new HashSet<>();
                deployment.getContainer().forEach(container -> containerPorts.addAll(container.getContainerPorts()));
                properties.addAll(createPropertiesFromMatchingService(services, deployment));
                component.setProperties(properties);

                ComponentType newComponentType = createTypeForComponent(component);
                newComponentTypes.add(newComponentType);
                component.setType(newComponentType);
                newComponents.add(component);
            }

            List<ComponentType> componentTypes = tadm.getComponentTypes();
            componentTypes.addAll(newComponentTypes);
            tadm.setComponentTypes(componentTypes);

            List<Component> components = tadm.getComponents();
            components.addAll(newComponents);
            tadm.setComponents(components);

            tadm = relationFinderService.findAndCreateRelations(tadm, newComponents, this.matchingServicesAndDeployments);

            return tadm;
        }

    /**
     * Create an EDMM artifact from a Docker image.
     * 
     * @param image
     * @return the created artifact.
     */
    private Artifact createArtifactFromImage(String image) {
        Artifact artifact = new Artifact();
        artifact.setName(image);
        artifact.setType("docker_image");
        artifact.setConfidence(Confidence.CONFIRMED);
        return artifact;
    }

    /**
     * Create EDMM properties from container port definitions.
     * If the container port has a name, set it as the key of the property.
     * 
     * @param containerPorts
     * @return the created properties.
     * @throws InvalidPropertyValueException
     */
    private List<Property> createPropertiesForContainerPorts(Set<ContainerPort> containerPorts) throws InvalidPropertyValueException {
        List<Property> properties = new ArrayList<>();
        for (ContainerPort containerPort : containerPorts) {
            Property property = new Property();
            if (containerPort.getName() == null) {
                property.setKey("container_port");
            } else {
                property.setKey(containerPort.getName());
            }
            property.setType(PropertyType.INTEGER);
            property.setValue(containerPort.getPort());
            property.setConfidence(Confidence.CONFIRMED);
            properties.add(property);
        }
        return properties;
    }

    /**
     * Create EDMM properties from environment variable definitions.
     * 
     * @param environmentVariables
     * @return the created properties.
     * @throws InvalidPropertyValueException
     */
    private List<Property> createPropertiesForEnvVariables(Set<EnvironmentVariable> environmentVariables) throws InvalidPropertyValueException {
        List<Property> properties = new ArrayList<>();
        for (EnvironmentVariable environmentVariable : environmentVariables) {
            Property property = new Property();
            property.setKey(environmentVariable.getKey());
            property.setType(PropertyType.STRING);
            property.setValue(environmentVariable.getValue());
            property.setConfidence(Confidence.CONFIRMED);
            properties.add(property);
        }
        return properties;
    }

    /**
     * Creates EDMM properties from matching services to deployments.
     * Iterates over all services and matches deployments where a selector of the service matches the label of a deployment.
     * Then creates a new property for each port of the service.
     * 
     * @param services
     * @param labels
     * @param containerPorts
     * @return the created properties.
     * @throws InvalidPropertyValueException
     */
    private List<Property> createPropertiesFromMatchingService(Set<KubernetesService> services, KubernetesDeployment deployment) throws InvalidPropertyValueException {        
        Set<ContainerPort> containerPorts = new HashSet<>();
        deployment.getContainer().forEach(container -> containerPorts.addAll(container.getContainerPorts()));        
        
        List<Property> properties = new ArrayList<>();
        for (KubernetesService service : services) {
            for (Selector selector : service.getSelectors()) {
                if (deployment.getLabels().stream().filter(label -> 
                    label.getKey().equals(selector.getKey()) &&
                    label.getValue().equals(selector.getValue())).count() > 0) {
                        this.matchingServicesAndDeployments.put(service, deployment);
                        Set<ServicePort> servicePorts = service.getServicePorts();                        
                        properties.addAll(createPropertiesFromMatchingPorts(servicePorts, containerPorts));
                }
            }
        }
        return properties;
    }

    /**
     * Creates EDMM properties from matching service ports to targeted container ports.
     * Adss the property value in the format "<port>:<targetPort>".
     * 
     * @param servicePorts
     * @param containerPorts
     * @return the created properties.
     * @throws InvalidPropertyValueException
     */
    private List<Property> createPropertiesFromMatchingPorts(Set<ServicePort> servicePorts, Set<ContainerPort> containerPorts) throws InvalidPropertyValueException {
        List<Property> properties = new ArrayList<>();
        for (ServicePort servicePort : servicePorts) {
            for (ContainerPort containerPort : containerPorts) {
                if (servicePort.getTargetPort() == containerPort.getPort()) {
                    Property property = new Property();
                    if (servicePort.getName() == null) {
                        property.setKey("external_port");
                    } else {
                        property.setKey(servicePort.getName());
                    }
                    property.setType(PropertyType.STRING);
                    property.setValue(servicePort.getPort()+":"+servicePort.getTargetPort());
                    property.setConfidence(Confidence.CONFIRMED);
                    properties.add(property);
                }
            }
        }
        return properties;
    }

    /**
     * From the given EDMM component, creates a EDMM componentType.
     * The componentType adopts all properties of the component but without a specific value.
     * 
     * @param component
     * @return the created componentType.
     * @throws InvalidPropertyValueException
     */
    private ComponentType createTypeForComponent(Component component) throws InvalidPropertyValueException {
        ComponentType componentType = new ComponentType();
        componentType.setName(component.getName()+"-type");

        List<Property> properties = new ArrayList<>();
        for (Property property : component.getProperties()) {
            Property typeProperty = new Property();
            typeProperty.setKey(property.getKey());
            typeProperty.setType(property.getType());
            typeProperty.setRequired(property.getRequired());
            properties.add(typeProperty);
        }
        componentType.setProperties(properties);
        return componentType;
    }
    
}
