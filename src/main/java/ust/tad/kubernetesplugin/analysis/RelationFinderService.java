package ust.tad.kubernetesplugin.analysis;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

    private String[] PROPERTY_KEYWORDS = {"connect","host","server","url","uri"};
    
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
            //newRelations.addAll(findRelationInPropertiesWithComponentNames(tadm, newComponents, matchingServicesAndDeployments));
            tadm.addRelations(newRelations);
            return tadm;
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
     * Iterates over the properties of a component to find relations to other components.
     * It uses the PROPERTY_KEYWORDS to find properties that may contain references to other components.
     * The values of these properties are inspected, whether they contain the name of another component.
     * If so, a new relation between the source component and the matched component is created.
     * 
     * @param tadm
     * @param sourceComponent
     * @param matchingServicesAndDeployments
     * @return the List of new relations that were created.
     * @throws InvalidRelationException
     * @throws URISyntaxException
     */
    private List<Relation> findRelationsInProperties(
        TechnologyAgnosticDeploymentModel tadm, 
        Component sourceComponent, 
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) 
        throws InvalidRelationException, URISyntaxException {
            List<Relation> newRelations = new ArrayList<>();
            List<String> targetComponentNames = tadm.getComponents().stream()
            .map(component -> component.getName())
            .collect(Collectors.toList());
            for (Property property : sourceComponent.getProperties()) {
                if (Arrays.stream(PROPERTY_KEYWORDS).anyMatch(property.getKey().toString().toLowerCase()::contains)) {
                    Optional<String> matchedComponentName = matchPropertyWithComponentNames(property, targetComponentNames);            
                    if (matchedComponentName.isPresent() && !matchedComponentName.get().equals(sourceComponent.getName())) {
                        Optional<Relation> relationOpt = createRelationToComponent(matchedComponentName.get(), sourceComponent, tadm.getComponents(), matchingServicesAndDeployments);
                        if (relationOpt.isPresent()) {
                            newRelations.add(relationOpt.get());
                        }
                    }
                }
            }
            return newRelations;
    }

    /**
     * Analyzes a property whether it contains a reference to another component through the name of the component.
     * If a match is found the name of the matched component is returned.
     * If there are several matches, the longest match is chosen, as there may be component names embedded in the 
     * names of other components (e.g., "my-service" and "my-service-db").
     * 
     * @param property
     * @param componentNames
     * @return an Optional with the name of the matched component.
     */
    private Optional<String> matchPropertyWithComponentNames(Property property, List<String> componentNames) {
        List<String> matchedComponentNames = componentNames.stream()
        .filter(targetComponentName -> property.getValue().toString().contains(targetComponentName))
        .collect(Collectors.toList());  
        if (matchedComponentNames.size() == 1) {
            return Optional.of(matchedComponentNames.get(0));
        } else if (matchedComponentNames.size() > 1) {
            return Optional.of(matchedComponentNames.stream().max(Comparator.comparingInt(String::length)).get());
        } else {
            return Optional.empty();
        }
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
