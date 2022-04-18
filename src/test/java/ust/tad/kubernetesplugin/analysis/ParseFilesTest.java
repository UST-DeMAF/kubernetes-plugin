package ust.tad.kubernetesplugin.analysis;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ust.tad.kubernetesplugin.models.tsdm.InvalidAnnotationException;
import ust.tad.kubernetesplugin.models.tsdm.InvalidNumberOfLinesException;

@SpringBootTest
public class ParseFilesTest {

    @Autowired
    AnalysisService analysisService;

    @Test
    public void parseService_success() throws IOException, InvalidNumberOfLinesException, InvalidAnnotationException {

        URL fileURL = new URL("file:/home/ubuntu/fork/kube/k8/order.yaml");

        analysisService.parseFile(fileURL);


    }
    
}
