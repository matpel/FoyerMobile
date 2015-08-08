package com.matpel.foyermobile;

import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class Get_products extends Thread {
    //Thread qui télécharge depuis l'API les infos sur les consos. On ne retient que le nom et le prix.
    MainActivity context;
    public int code=0;
    public boolean erreur=false;
    private String token;
    private URL url_site;


    Get_products(MainActivity c,URL url_site,String token) {
        context = c;
        this.token=token;
        this.url_site=url_site;
    }

    @Override
    public void run() {
        HttpURLConnection connexion =null;
        try {
            URL url = new URL(url_site.toString()+"/api/beers");
            connexion = (HttpURLConnection) url.openConnection();
            connexion.setRequestMethod("GET");
            connexion.setRequestProperty("Authorization","Bearer "+token);
            connexion.setUseCaches(true);
            connexion.connect();
            erreur=(connexion.getResponseCode()!=200);
            code=connexion.getResponseCode();
            Log.d("retour",""+code);
            if(erreur){
                context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "Erreur telechargement stocks: " + code, Toast.LENGTH_SHORT).show();
                }
                });
            }
            BufferedInputStream bis = new BufferedInputStream(connexion.getInputStream());
            byte[] contents = new byte[1024];
            File file=new File(context.getFilesDir()+"/stocks.txt");
            BufferedOutputStream bos=new BufferedOutputStream(new FileOutputStream(context.getFilesDir()+"/stocks.txt"));
            int j;
            while ((j=bis.read()) != -1) {
                bos.write(j);
            }
            bos.flush();
            bos.close();
            } catch (IOException e) {
            e.printStackTrace();
        }finally {
            assert connexion != null;
            connexion.disconnect();
        }
    }
}