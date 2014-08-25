package org.wikibrain.atlasify;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.SRResult;
import sun.net.www.content.text.plain;

import java.awt.Color;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// The Java class will be hosted at the URI path "/helloworld"
@Path("/wikibrain")
public class AtlasifyResource {

    private static class AtlasifyQuery{
        private String keyword;
        private String[] input;

        public AtlasifyQuery(){

        }

        public AtlasifyQuery(String keyword, String[] input){
            this.keyword = keyword;
            this.input = input;
        }

        public AtlasifyQuery(String keyword, List<String> input){
            this.keyword = keyword;
            this.input = input.toArray(new String[input.size()]);
        }

        public String getKeyword(){
            return keyword;
        }

        public String[] getInput(){
            return input;
        }

    }

    private static SRMetric sr = null;

    private static void wikibrainSRinit(){

        try {
            Env env = new EnvBuilder().build();
            Configurator conf = env.getConfigurator();
            LocalPageDao lpDao = conf.get(LocalPageDao.class);

            Language simple = Language.getByLangCode("simple");

            sr = conf.get(
                    SRMetric.class, "ensemble",
                    "language", simple.getLangCode());


        } catch (ConfigurationException e) {
            System.out.println("Configuration Exception: "+e.getMessage());
        }

    }

    // The Java method will process HTTP GET requests
    @GET
    // The Java method will produce content identified by the MIME Media
    // type "text/plain"
    @Path("/SR/keyword={keyword}&feature=[{input}]")
    @Consumes("text/plain")
    @Produces("text/plain")
    public Response getClichedMessage(@PathParam("keyword") String keyword, @PathParam("input") String data) throws  DaoException{
        if(sr == null){
            wikibrainSRinit();
        }
        String[] features = data.split(",");
        Map<String, String> srMap = new HashMap<String, String>();
        for(int i = 0; i < features.length; i++){
            srMap.put(features[i].toString(), getColorStringFromSR(sr.similarity(keyword, features[i].toString(), false).getScore()));
        }
        return Response.ok(new JSONObject(srMap).toString()).header("Access-Control-Allow-Origin", "*").build();
    }
/*
    @POST
    @Path("/send")
    @Produces("text/plain")
    public Response nullResponse () {
        return Response.ok("success").build();
    }
*/
    @POST
    @Path("/send")
    @Consumes("application/json")
    @Produces("text/plain")

    public Response consumeJSON (AtlasifyQuery query) throws DaoException{
        if(sr == null){
            wikibrainSRinit();
        }
        String[] features = query.getInput();
        Map<String, String> srMap = new HashMap<String, String>();
        for(int i = 0; i < features.length; i++){
            String color = "#ffffff";
            try {
                color = getColorStringFromSR(sr.similarity(query.getKeyword(), features[i].toString(), false).getScore());
            }
            catch (Exception e){
                //do nothing
            }

            srMap.put(features[i].toString(), color);
        }
        return Response.ok(new JSONObject(srMap).toString()).build();

    }



    private String getColorStringFromSR(double SR){
        if(SR < 0.2873)
            return "#ffffff";
        if(SR < 0.3651)
            return "#f7fcf5";
        if(SR < 0.4095)
            return "#e5f5e0";
        if(SR < 0.4654)
            return "#c7e9c0";
        if(SR < 0.5072)
            return "#a1d99b";
        if(SR < 0.5670)
            return "#74c476";
        if(SR < 0.6137)
            return "#41ab5d";
        if(SR < 0.6809)
            return "#238b45";
        if(SR < 0.7345)
            return "#006d2c";
        if(SR < 0.7942)
            return "#00441b";
        return "#002000";
    }


}
