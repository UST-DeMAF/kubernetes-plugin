package ust.tad.kubernetesplugin.analysis;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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
    public void parseService_success() throws IOException, InvalidNumberOfLinesException, InvalidAnnotationException, URISyntaxException {

        String connecString = "jdbc:postgresql://postgres-inventory:5432/inventory";
        URI connectionURI = new URI(connecString.replaceFirst("jdbc:", ""));
        System.out.println("Scheme:"+connectionURI.getScheme());
        System.out.println("Authority:"+connectionURI.getAuthority());
        System.out.println("Host:"+connectionURI.getHost());
        System.out.println("UserInfo:"+connectionURI.getUserInfo());
        System.out.println("Path:"+connectionURI.getPath());

    }
    
}
