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

public class Get_clients extends Thread{
    //thread qui télécharge les noms prénoms et ids des clients (même fonction que pour les stocks, la seule différence sont les urls... copier/coller de feignasse)
    MainActivity context;
    public int code=0;
    public boolean erreur=false;
    private URL url_site=null;
    private String token=null;

    Get_clients(MainActivity c,URL url_site,String token) {
        context = c;
        this.url_site=url_site;
        this.token=token;
    }

    @Override
    public void run() {
        HttpURLConnection connexion =null;

        try {
            URL url = new URL(url_site.toString()+"/api/users?limit=10000");
            //Authenticator.setDefault(new CustomAuthenticator(id,mdp));
            connexion = (HttpURLConnection) url.openConnection();
            connexion.setRequestProperty("Authorization", "Bearer "+token);
            connexion.setUseCaches(true);
            connexion.connect();
            erreur=(connexion.getResponseCode()!=200);
            code=connexion.getResponseCode();
            Log.d("retour",""+code);
            if(erreur){
                context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "Erreur telechargement clients: " + code, Toast.LENGTH_SHORT).show();
                }
                });
            }
            BufferedInputStream bis = new BufferedInputStream(connexion.getInputStream());
            byte[] contents = new byte[1024];
            int bytesRead=0;
            File file=new File(context.getFilesDir()+"/clients.txt");
            BufferedOutputStream bos=new BufferedOutputStream(new FileOutputStream(file));
            int i;
            while ((i=bis.read()) != -1) {
                bos.write(i);
            }
            bos.flush();
            bos.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
}
