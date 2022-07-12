package ust.tad.kubernetesplugin.analysis;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ust.tad.kubernetesplugin.kubernetesmodel.deployment.KubernetesDeployment;
import ust.tad.kubernetesplugin.kubernetesmodel.service.KubernetesService;
import ust.tad.kubernetesplugin.models.tadm.Component;
import ust.tad.kubernetesplugin.models.tadm.ComponentType;
import ust.tad.kubernetesplugin.models.tadm.Confidence;
import ust.tad.kubernetesplugin.models.tadm.InvalidRelationException;
import ust.tad.kubernetesplugin.models.tadm.Property;
import ust.tad.kubernetesplugin.models.tadm.Relation;
import ust.tad.kubernetesplugin.models.tadm.RelationType;
import ust.tad.kubernetesplugin.models.tadm.TechnologyAgnosticDeploymentModel;

@Service
public class RelationFinderService {
    
    private RelationType connectsToRelationType = new RelationType();
    private RelationType hostedOnRelationType = new RelationType();

    /**
     * Creates EDMM relations for the newly created components.
     * 
     * @param tadm
     * @param newComponents
     * @param matchingServicesAndDeployments
     * @return
     * @throws InvalidRelationException
     * @throws URISyntaxException
     */
    public TechnologyAgnosticDeploymentModel findAndCreateRelations(
        TechnologyAgnosticDeploymentModel tadm, 
        List<Component> newComponents, 
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) 
        throws InvalidRelationException, URISyntaxException {
            setRelationTypes(tadm.getRelationTypes());
            Set<Relation> newRelations = new HashSet<>();

            for (Component newComponent : newComponents) {
                newRelations.addAll(findRelationsInProperties(tadm, newComponent, matchingServicesAndDeployments));
                Optional<Relation> relationToContainerRuntime = findRelationToContainerRuntime(tadm, newComponent);
                if (relationToContainerRuntime.isPresent()) {
                    newRelations.add(relationToContainerRuntime.get());
                }
            }   
            
            newRelations.addAll(findRelationInPropertiesWithComponentNames(tadm, newComponents, matchingServicesAndDeployments));

            List<Relation> relations = tadm.getRelations();
            relations.addAll(newRelations);
            tadm.setRelations(relations);
            return tadm;
    }

    /**
     * Find relations in the properties of the newly created Components.
     * 
     * @param tadm
     * @param newComponents
     * @param matchingServicesAndDeployments
     * @return
     * @throws InvalidRelationException
     */
    private List<Relation> findRelationInPropertiesWithComponentNames(TechnologyAgnosticDeploymentModel tadm, 
        List<Component> newComponents, 
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) throws InvalidRelationException {
        String[] keywords = {"connect","host","server"};
        List<Relation> newRelations = new ArrayList<>();
        List<String> componentNames = newComponents.stream().map(component -> component.getName()).collect(Collectors.toList());
        for (Component sourceComponent : newComponents) {
            for (Property property : sourceComponent.getProperties()) {
                for (String targetComponentName : componentNames) {
                    if (!targetComponentName.equals(sourceComponent.getName())) {
                        String propertyKey = property.getKey().toString().toLowerCase();
                        if (Arrays.stream(keywords).anyMatch(propertyKey::contains) &&
                            property.getValue().toString().trim().replaceAll("\"", "").equals(targetComponentName)) {
                            Optional<Relation> relationOpt = createRelationToComponent(targetComponentName, sourceComponent, tadm.getComponents(), matchingServicesAndDeployments);
                            if (relationOpt.isPresent()) {
                                newRelations.add(relationOpt.get());
                            }
                        }
                    }
                }
            }
        }
        return newRelations;
    }


    /**
     * Finds and creates an EDMM relation of type "hosted on" between a newly created component and an 
     * already existing component of type "container runtime".
     * 
     * @param tadm
     * @param newComponent
     * @return
     * @throws InvalidRelationException
     */
    private Optional<Relation> findRelationToContainerRuntime(TechnologyAgnosticDeploymentModel tadm, Component newComponent) throws InvalidRelationException {
        Optional<ComponentType> containerRuntimeComponentTypeOpt = tadm.getComponentTypes().stream().filter(componentType -> componentType.getName().equals("container_runtime")).findFirst();
        if (containerRuntimeComponentTypeOpt.isPresent()) {
            Optional<Component> containerRuntimeComponentOpt = tadm.getComponents().stream().filter(component -> component.getType().equals(containerRuntimeComponentTypeOpt.get())).findFirst();
            if(containerRuntimeComponentOpt.isPresent()) {
                Relation relation = new Relation();
                relation.setType(this.hostedOnRelationType);
                relation.setName(newComponent.getName()+"_"+this.hostedOnRelationType.getName()+"_"+containerRuntimeComponentOpt.get().getName());
                relation.setSource(newComponent);
                relation.setTarget(containerRuntimeComponentOpt.get());
                relation.setConfidence(Confidence.SUSPECTED);
                return Optional.of(relation);
            }
        }
        return Optional.empty();
    }

    /**
     * Iterates over the properties of a newly created component to find relations to other components.
     * 
     * @param tadm
     * @param newComponent
     * @param matchingServicesAndDeployments
     * @return
     * @throws InvalidRelationException
     * @throws URISyntaxException
     */
    private List<Relation> findRelationsInProperties(
        TechnologyAgnosticDeploymentModel tadm, 
        Component newComponent, 
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) 
        throws InvalidRelationException, URISyntaxException {
            List<Relation> newRelations = new ArrayList<>();

            for (Property property : newComponent.getProperties()) {
                String targetComponentName = "";
                if (property.getKey().equals("SPRING_DATASOURCE_URL")) {
                    targetComponentName = new URI(property.getValue().toString().replaceFirst("jdbc:", "")).getHost();
                } else if (property.getValue().toString().startsWith("http")) {
                    targetComponentName = new URI(property.getValue().toString()).getHost();
                } else if (property.getKey().equals("MONGO_HOST")) {
                    targetComponentName = property.getValue().toString();
                } else if (property.getKey().contains("KAFKA_BOOTSTRAP_SERVERS")) {
                    targetComponentName = property.getValue().toString().split(":")[0];
                } else if (property.getKey().contains("ZOOKEEPER_CONNECTION_STRING")) {
                    targetComponentName = property.getValue().toString().split(":")[0]; 
                }

                if (!targetComponentName.equals("")) {
                    Optional<Relation> relationOpt = createRelationToComponent(targetComponentName, newComponent, tadm.getComponents(), matchingServicesAndDeployments);
                    if (relationOpt.isPresent()) {
                        newRelations.add(relationOpt.get());
                    }
                }

            }
            return newRelations;
    }

    /**
     * Create an EDMM relation between a source and a target component of type "connects to".
     * For that, finds the target component by the given targetComponentName.
     * If it cannot find the component, it creates no relation.
     * 
     * @param connectionURI
     * @param sourceComponent
     * @param components
     * @param matchingServicesAndDeployments
     * @return
     * @throws MalformedURLException
     * @throws InvalidRelationException
     * @throws URISyntaxException
     */
    private Optional<Relation> createRelationToComponent(
        String targetComponentName, 
        Component sourceComponent, 
        List<Component> components,
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) 
        throws InvalidRelationException {
            Relation relation = new Relation();
            relation.setType(this.connectsToRelationType);
            relation.setSource(sourceComponent);
            relation.setConfidence(Confidence.CONFIRMED);

            Optional<Component> targetComponentOpt = getComponentByName(targetComponentName, components);
            if (targetComponentOpt.isPresent()) {
                relation.setTarget(targetComponentOpt.get());
                relation.setName(sourceComponent.getName()+"_"+this.connectsToRelationType.getName()+"_"+targetComponentOpt.get().getName());
                return Optional.of(relation);
            }
            targetComponentOpt = getComponentByMatchingService(targetComponentName, matchingServicesAndDeployments, components);
            if (targetComponentOpt.isPresent()) {
                relation.setTarget(targetComponentOpt.get());
                relation.setName(sourceComponent.getName()+"_"+this.connectsToRelationType.getName()+"_"+targetComponentOpt.get().getName());
                return Optional.of(relation);
            }

            return Optional.empty();
    }

    /**
     * Get a component by its name from a given List of components.
     * 
     * @param name
     * @param components
     * @return
     */
    private Optional<Component> getComponentByName(String name, List<Component> components) {
        return components.stream().filter(component -> component.getName().equals(name)).findFirst();
    }

    /**
     * For a given serviceName, finds the EDMM component that was created based on this service.
     * 
     * @param serviceName
     * @param matchingServicesAndDeployments
     * @param components
     * @return
     */
    private Optional<Component> getComponentByMatchingService(String serviceName, Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments, List<Component> components) {
        Set<KubernetesService> services = matchingServicesAndDeployments.keySet();
        Optional<KubernetesService> matchedService = services.stream().filter(s -> s.getName().equals(serviceName)).findFirst();
        if (matchedService.isPresent()) {
            String componentName = matchingServicesAndDeployments.get(matchedService.get()).getName();
            Optional<Component> component = components.stream().filter(c -> c.getName().equals(componentName)).findFirst();
            if(component.isPresent()) {
                return component;
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the "connects to" and "hosted on" relation types from the technology-agnostic deployment model.
     * Saves them in class-wide variables for reuse in newly created components.
     * 
     * @param relationTypes
     */
    private void setRelationTypes(List<RelationType> relationTypes) {
        Optional<RelationType> connectsToRelationTypeOpt = relationTypes.stream().filter(relationType -> relationType.getName().equals("ConnectsTo")).findFirst();
        if (connectsToRelationTypeOpt.isPresent()) {
            this.connectsToRelationType = connectsToRelationTypeOpt.get();
        }
        Optional<RelationType> hostedOnRelationTypeOpt = relationTypes.stream().filter(relationType -> relationType.getName().equals("HostedOn")).findFirst();
        if (hostedOnRelationTypeOpt.isPresent()) {
            this.hostedOnRelationType = hostedOnRelationTypeOpt.get();
        }
    }
    
}
