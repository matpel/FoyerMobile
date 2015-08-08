package com.matpel.foyermobile;

import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * Created by Mathias on 16/05/2015.
 */
public class Sync extends Thread {
    private String s=null;
    public int code=0;
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
        HttpURLConnection connect=null;
        try {
            URL url=new URL(url_site.toString()+"/api/beers/"+beer+"/users/"+eleve);
            connect=(HttpURLConnection)url.openConnection();
            connect.setDoOutput(true);
            connect.setRequestMethod("POST");
            connect.setRequestProperty("Authorization","Bearer "+token);
            code=connect.getResponseCode();
            Log.d("code sync",""+url.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            if(connect!=null)
                connect.disconnect();
        }
    }
}
