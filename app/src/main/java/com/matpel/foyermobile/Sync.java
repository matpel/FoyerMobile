package com.matpel.foyermobile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by Mathias on 16/05/2015.
 */
public class Sync extends Thread {
    private String s=null;
    public int code = -1;
    private  String token=null;
    public boolean erreur=true;
    URL url_site;
    String beer;
    String eleve;

    Sync(URL url_site,String token,String beer,String eleve) {
        this.url_site=url_site;
        this.token=token;
        this.beer=beer;
        this.eleve=eleve;
    }
    @Override
    public void run() {
        HttpsURLConnection connect = null;
        try {
            String jsonParam = "user=" + eleve + "&beer=" + beer;
            URL url = new URL(url_site.toString() + "/api/transactions");
            connect = (HttpsURLConnection) url.openConnection();
            connect.setDoInput(true);
            connect.setDoOutput(true);
            connect.setRequestProperty("Authorization", "Bearer " + token);
            connect.setRequestMethod("POST");
            OutputStream os = connect.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(jsonParam);
            writer.flush();
            writer.close();
            os.close();
            connect.connect();
            code=connect.getResponseCode();
            s = connect.getResponseMessage();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //Log.d("code sync",s);
            if(connect!=null)
                connect.disconnect();
        }
    }
}
