package com.matpel.foyermobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Vector;


public class MainActivity extends Activity {

    private  URL url_site;
    private String token; //token fourni par l'APi uPont suite à la requête /login/username/id/password/mdp
    private int code=0;//code erreur retourné par l'API
    private ListView hist=null; //historique des consos stockées sur le mobile
    private LinearLayout layout=null;
    private MultiAutoCompleteTextView nom = null; //le TextView permettant d'entrer les (les) nom(s) de(s) élève(s) pour enregistrer une conso
    private AutoCompleteTextView conso = null;//champ permettant d'entrer UNE conso pour tout les élèves inscrits dans le champ nom
    private TextView info = null;//textview indiquant la dernière entrée (géré par getlastlog())
    private TextView solde=null;//champ indiquant le solde du dernier élève entré dans le champ nom
    private TextView prix=null;//champ indiquant le prix de la bière entrée dans conso

    private String id=null;
    private String mdp=null;

    private static Vector<String> listNoms = new Vector<>(0);//Vector contenant les noms de TOUT les élèves
    private static Vector<String> listIdNom = new Vector<>(0);//Vector contenant les identifiants de tout les élèves, dans le même ordreque listNoms (par ex. peluchom pour Mathias Peluchon)
    private static Vector<String> listConso = new Vector<>(0);//Vector contenant les noms de toutes les consos
    private static Vector<String> listIdConsos = new Vector<>(0);//idem que pour les noms des élèves
    private static Vector<Double> listSoldes=new Vector<>(0);//vector contnant les soldes de tout les élèves
    private static Vector<Double> listPrix=new Vector<>(0);//vector contenant les prix des bières

    private Vector<String> listHist=new Vector<>(0);//vector contenant l'historique des entrées stockées sur le mobile

    ArrayAdapter<String> adaptaterNoms = null;//adaptater permettant de peuplet l'autocomplete des noms
    ArrayAdapter<String> adaptaterConso = null;//idem pour les consos
    ArrayAdapter<String> adaptaterHist=null;//idem pour le listView de l'historique

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            url_site=new URL("https://upont.enpc.fr");//Si l'url du site change, changer cette valeur
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        hist=(ListView)findViewById(R.id.list);
        layout=(LinearLayout) findViewById(R.id.mainlayout);
        ImageButton param = (ImageButton) findViewById(R.id.param);
        nom = (MultiAutoCompleteTextView) findViewById(R.id.Nomprenom);
        conso = (AutoCompleteTextView) findViewById(R.id.conso);
        Button ok = (Button) findViewById(R.id.boutonOk);
        info = (TextView) findViewById(R.id.info);
        solde=(TextView) findViewById(R.id.solde);
        prix=(TextView)findViewById(R.id.prix);
        info.setText(getlastlog("registre.txt"));
        nom.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        adaptaterNoms = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listNoms);
        adaptaterConso = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listConso);

        try {
            updateData(false);//on lit remplit les Vector élèves/consos d'après la dernière sauvegarde effectuée sur le mobile dans clients.txt et stocks.txt (false=pas de synchronisation via l'API, ce n'est pas utile de le faire à chaque fois).
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //définition des adaptater peuplant les listes (historique, et listes déroulantes pour eleve/conso)
        adaptaterHist= new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listHist);
        hist.setAdapter(adaptaterHist);
        nom.setAdapter(adaptaterNoms);
        conso.setAdapter(adaptaterConso);
        nom.setThreshold(1);
        conso.setThreshold(1);
        nom.setDropDownAnchor(R.id.Nomprenom);
        conso.setDropDownAnchor(R.id.conso);

        //Definition des listeners
        ok.setOnClickListener(okListener);
        nom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nom.setText("");
            }
        });
        conso.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                conso.setText("");
            }
        });

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR1) {
            //ce testest nécessaire pour définir le OnDismissListener plus bas
            nom.setOnDismissListener(new AutoCompleteTextView.OnDismissListener() {
                @Override
                public void onDismiss() {
                    try {
                        String nomString=nom.getText().toString();
                        String nomEntresArray[]=nomString.substring(0,nomString.length()-2).split(", ");
                        Double s = listSoldes.elementAt(listNoms.indexOf(nomEntresArray[nomEntresArray.length-1]));//on inscrit le solde du dernier entré
                        solde.setText("" + s + "\u20AC");
                    } catch (IndexOutOfBoundsException e) {
                        if(nom.getText().toString().length()>0)
                        Toast.makeText(getApplicationContext(),"Erreur lecture solde",Toast.LENGTH_SHORT).show();
                    }
                }
            });
            //OnDismissListener pour AutoCompleteTextView n'existe que sur les API>=17
            conso.setOnDismissListener(new AutoCompleteTextView.OnDismissListener() {
                @Override
                public void onDismiss() {
                    try {
                        Double s = listPrix.elementAt(listConso.indexOf(conso.getText().toString()));
                        prix.setText("" + s + "\u20AC");
                    } catch (IndexOutOfBoundsException e) {
                        if(conso.getText().toString().length()>0)
                        Toast.makeText(getApplicationContext(),"Erreur lecture prix",Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        registerForContextMenu(param);//on crée le contextMenu quis'ouvre en appuyant longuement sur le bouton en haut à droite
    }



    @Override
    public void onCreateContextMenu(ContextMenu m, View p, ContextMenu.ContextMenuInfo menuInfo) {

        super.onCreateContextMenu(m, p, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, m);
    }


    View.OnClickListener okListener = new View.OnClickListener() {
        //on écrit la/les conso(s) dans le fichier registre.txt
        @Override
        public void onClick(View v) {
            try {
                OutputStreamWriter out = new OutputStreamWriter(openFileOutput("registre.txt", MODE_APPEND));

                boolean bnom = false;
                boolean bconso = false;
                String string=nom.getText().toString();
                String multiNoms[]=string.substring(0,string.length()-2).split(", ");//Le MultiAutocomplete rajoute un ", " à la fin de chaque nom, il faut l'éliminer, puis on sépare les noms
                for(String j:multiNoms) {
                    for (String i : listNoms)
                        bnom = bnom || (i.equals(j));
                    for (String i : listConso)
                        bconso = bconso || (i.equals(conso.getText().toString()));
                    if (bconso && bnom) {
                        out.write("*" +j + ":" + conso.getText().toString());
                        Toast.makeText(getApplicationContext(), "OK !", Toast.LENGTH_SHORT).show();
                    } else if (bconso)
                        Toast.makeText(getApplicationContext(), "Conso inexistante: "+conso.getText().toString(), Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(getApplicationContext(), "Mauvais nom/prénom: "+j, Toast.LENGTH_SHORT).show();
                }
                out.close();
            } catch (java.io.IOException e) {
                Toast.makeText(getApplicationContext(), "Impossible d'ecrire dans le fichier", Toast.LENGTH_SHORT).show();
            }
            info.setText(getlastlog("registre.txt"));

        }
    };





    @Override
    public boolean onContextItemSelected(MenuItem item) {

        //actions à effectuer selon l'action choisie dans le contextMenu
        switch (item.getItemId()) {
            case R.id.suplast: {
                //si on veut supprimer la dernière entrée
                int i = -1;
                String s = "";
                try {
                    InputStreamReader file = new InputStreamReader(openFileInput("registre.txt"));
                    BufferedReader buffreader = new BufferedReader(file);
                    String line = "";
                    try {
                        while ((line = buffreader.readLine()) != null)
                            s += line;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                i = s.lastIndexOf((char) '*');
                if (i > -1) {
                    try {
                        OutputStreamWriter out = new OutputStreamWriter(openFileOutput("registre.txt", MODE_PRIVATE));
                        String s2 = s.substring(i + 1, s.length());
                        s = s.substring(0, i);
                        out.write(s);
                        out.close();
                        Toast.makeText(getApplicationContext(), "Supprimé " + s2, Toast.LENGTH_SHORT).show();
                    } catch (FileNotFoundException e) {
                        Toast.makeText(getApplicationContext(), "Impossible d'ouvrir le fichier registre.txt", Toast.LENGTH_SHORT);
                    } catch (IOException e) {
                        Toast.makeText(getApplicationContext(), "Impossible d'ecrire dans le fichier registre.txt", Toast.LENGTH_SHORT);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Rien à effacer", Toast.LENGTH_SHORT).show();
                }
                info.setText(getlastlog("registre.txt"));
                return true;
            }
            case R.id.supall: {
                //si on veut tout supprimer
                deleteFile("registre.txt");
                Toast.makeText(getApplicationContext(), "Toutes les entrées ont été supprimées", Toast.LENGTH_SHORT).show();
                info.setText("Aucune entrée à supprimer");
                return true;
            }

            case R.id.identifiants:{
                //si on veut entrer les identifiants
                getCred();
                return true;
            }

            case R.id.sync: {
                //si on veut synchroniser sur le site
                boolean b;
                if(token==null){Toast.makeText(getApplicationContext(),"Connecte-toi pour synchroniser",Toast.LENGTH_SHORT).show();return true;}
                    try {
                        InputStreamReader file = new InputStreamReader(openFileInput("registre.txt"));
                        BufferedReader buffreader = new BufferedReader(file);
                        String line = "", s = "";
                        try {
                            while ((line = buffreader.readLine()) != null)
                                s += line;
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "Impossible de lire le fichier registre.txt", Toast.LENGTH_SHORT).show();
                        }//s contient tout le fichier

                        int i = 0;
                        s = s + '*';//le format d'écriture des entrées dans registre.txt est *nom_eleve1:conso1*nom_eleve2:conso2....
                        Toast.makeText(getApplicationContext(), "Synchronisation...", Toast.LENGTH_SHORT).show();
                        while (s.indexOf('*', i) < s.length() - 1) {
                            int k1 = 0, k2 = 0;
                            k1 = listNoms.indexOf(s.substring(s.indexOf('*', i) + 1, s.indexOf(':', i)));
                            k2 = listConso.indexOf(s.substring(s.indexOf(':', i) + 1, s.indexOf('*', i + 1)));
                            Sync sync = new Sync(url_site, token,listIdConsos.elementAt(k2),listIdNom.elementAt(k1));
                            sync.start();
                            sync.join();
                            if (sync.code!=204) {
                                if(sync.code==401)
                                    Toast.makeText(getApplicationContext(), "Erreur 401, problème d'identification lors de la synchronisation de"+listNoms.elementAt(k1)+ " "+listConso.elementAt(k2)+". Les éléments précédents dans l'historique ont été synchronisés ", Toast.LENGTH_SHORT).show();
                                else
                                    Toast.makeText(getApplicationContext(), "Erreur "+sync.code+" lors de la synchronisation de "+listNoms.elementAt(k1)+ " "+listConso.elementAt(k2)+". Les éléments précédents dans l'historique ont été synchronisés", Toast.LENGTH_SHORT).show();
                                return true;
                            }
                            i = s.indexOf('*', i + 1);
                        }
                        Toast.makeText(getApplicationContext(), "Synchronisation terminée! Pense à tout supprimer!", Toast.LENGTH_SHORT).show();

                    } catch (FileNotFoundException e) {
                        Toast.makeText(getApplicationContext(), "Fichier registre.txt introuvable", Toast.LENGTH_SHORT).show();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                return true;
            }
            case R.id.historique:{
                editHist();
                return true;
            }
            case R.id.getdata: {
                if(id!=null) {
                    try {
                        updateData(true);//on synchronise via l'API (true) la liste des élèves et celle des consos
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }


        }
        return super.onOptionsItemSelected(item);
    }

    private String getlastlog(String name) {
        //retourne la denrière entrée dans le registre
        String s = "Aucune entrée";
        try {
            InputStreamReader file = new InputStreamReader(openFileInput(name));
            BufferedReader buffreader = new BufferedReader(file);
            String line = "";
            try {
                while ((line = buffreader.readLine()) != null)
                    s += line;
                file.close();
            } catch (Exception e) {
                return "Aucune entrée";
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int i = s.lastIndexOf('*');
        int j = s.lastIndexOf(':');
        if (i > -1) {
            String s2 = s.substring(i + 1, s.length());
            s = s.substring(i + 1, j) + " - " + s.substring(j + 1, s.length());
        }
        return s;
    }

    private void updateData(boolean internet) throws JSONException {
        //met à jour les Vector concernant les consos et les noms. Si internet==true on fait appel à l'api, sinon on récupère juste les données sauvegarfées dans clients.txt et stocks.txt
        listConso.removeAllElements();
        listIdNom.removeAllElements();
        listIdConsos.removeAllElements();
        listPrix.removeAllElements();
        listNoms.removeAllElements();
        listSoldes.removeAllElements();

        StringBuilder response = new StringBuilder();
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (internet && networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
            Get_clients t = new Get_clients(this,url_site,token);
            t.start();
            try {
                t.join();
                boolean err=t.erreur;
                code=t.code;
                if(!err)
                    Toast.makeText(getApplicationContext(), "Liste des élèves téléchargée depuis le site", Toast.LENGTH_SHORT).show();
                else if (code==401){Toast.makeText(getApplicationContext(),"Erreur "+code+". Problème d'identification.",Toast.LENGTH_SHORT).show();return;}
                else{Toast.makeText(getApplicationContext(),"Erreur "+code,Toast.LENGTH_SHORT);}
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if(internet) Toast.makeText(getApplicationContext(), "Pas de réseau", Toast.LENGTH_SHORT).show();

        //LECTURE FICHIER SAUVEGARDE CLIENTS
        String s = "";
        try {//on met le fichier dans String s
            File streamFile = new File(getFilesDir() + "/clients.txt");
            FileInputStream filei = new FileInputStream(streamFile);
            InputStreamReader file = new InputStreamReader(filei, "UTF-8");
            BufferedReader buffreader = new BufferedReader(file);
            String temp;
            while ((temp=buffreader.readLine()) != null)
                s += temp;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),"Erreur ouverture fichier clients.txt",Toast.LENGTH_SHORT);
            return;
        }//s contient tout le fichier

        JSONArray json=new JSONArray(s);
        for(int i=0;i<json.length();i++){
            listNoms.add(json.getJSONObject(i).optString("first_name")+" "+json.getJSONObject(i).optString("last_name"));
            listIdNom.add(json.getJSONObject(i).optString("username"));
            listSoldes.add(json.getJSONObject(i).optDouble("solde"));
        }
        adaptaterNoms.notifyDataSetChanged();
        Toast.makeText(getApplicationContext(),"Liste des eleves à jour",Toast.LENGTH_SHORT).show();

        /////On met à jour les stocks après les clients
        if (internet && networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
            Get_products t = new Get_products(this,url_site,token);
            t.start();
            try {
                t.join();
                Toast.makeText(getApplicationContext(), "Stock téléchargés depuis le site", Toast.LENGTH_SHORT).show();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if(internet) Toast.makeText(getApplicationContext(), "Pas de réseau", Toast.LENGTH_SHORT).show();

        //Lecture fichier sauvegarde consos
        s = "";
        try {//on met le fichier dans String s
            File streamFile = new File(getFilesDir() + "/stocks.txt");
            FileInputStream filei = new FileInputStream(streamFile);
            InputStreamReader file = new InputStreamReader(filei, "UTF-8");
            BufferedReader buffreader = new BufferedReader(file);
            String temp;
            while ((temp=buffreader.readLine()) != null)
                s += temp;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Erreur ouverture fichier stocks.txt",Toast.LENGTH_SHORT).show();
            return;
        }//s contient tout le fichier
        json = new JSONArray(s);
        for(int i=0;i<json.length();i++){
            listConso.add(json.getJSONObject(i).optString("name"));
            listIdConsos.add(json.getJSONObject(i).optString("slug"));
            listPrix.add(json.getJSONObject(i).optDouble("price"));
        }
        adaptaterConso.notifyDataSetChanged();
        Toast.makeText(getApplicationContext(),"Stock à jour",Toast.LENGTH_SHORT).show();
    }

    private void getCred(){
        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        final View yourCustomView = inflater.inflate(R.layout.activity_id, null);

        final TextView textId = (EditText) yourCustomView.findViewById(R.id.id);
        final TextView textMdp = (EditText) yourCustomView.findViewById(R.id.mdp);
        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Identification")
                .setView(yourCustomView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        id = textId.getText().toString();
                        mdp = textMdp.getText().toString();

                        Thread t = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                HttpURLConnection connect = null;
                                String strJson = "";
                                try {
                                    URL url = new URL(url_site.toString() + "/api/login");
                                    connect = (HttpURLConnection) url.openConnection();
                                    connect.setDoOutput(true);
                                    connect.setRequestMethod("POST");
                                    OutputStreamWriter os = new OutputStreamWriter(connect.getOutputStream());
                                    os.write("username=" + id + "&password=" + mdp);
                                    os.flush();
                                    boolean erreur = (connect.getResponseCode() / 100 != 2);
                                    code = connect.getResponseCode();
                                    if (erreur) {
                                        MainActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                token=null;
                                                Toast.makeText(MainActivity.this,"Erreur "+code+" lors de la connexion à uPont",Toast.LENGTH_SHORT).show();
                                                if(code==401)
                                                    Toast.makeText(MainActivity.this,"Mauvais identifiants/non membre du foyer",Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        return;
                                    }
                                    MainActivity.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getApplicationContext(), "Connecté à uPont !", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    InputStream is = connect.getInputStream();
                                    BufferedInputStream bis = new BufferedInputStream(connect.getInputStream());
                                    byte[] contents = new byte[1024];
                                    int bytesRead = 0;
                                    while ((bytesRead = bis.read(contents)) != -1) {
                                        strJson += new String(contents, 0, bytesRead);
                                    }
                                    JSONObject json = new JSONObject(strJson);
                                    token = json.optString("token");

                                } catch (IOException | JSONException e1) {
                                    e1.printStackTrace();
                                } finally {
                                    assert connect != null;
                                    connect.disconnect();
                                }
                            }
                        });
                        t.start();
                        try {
                            t.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Annuler", null).create();
        dialog.show();
    }

    private void editHist(){
        listHist.removeAllElements();
        InputStreamReader file = null;
        try {
            file = new InputStreamReader(openFileInput("registre.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        BufferedReader buffreader = new BufferedReader(file);
        String line = "", s = "", strurl = "";
        try {
            while ((line = buffreader.readLine()) != null)
                s += line;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Impossible de lire le fichier", Toast.LENGTH_SHORT).show();
        }finally{
            try {
                file.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }//s contient tout le fichier
        final String s2=s;


        s=s+'*';
        while(s.indexOf('*')<s.length()-1){
            s=s.substring(s.indexOf('*')+1);
            listHist.add(s.substring(0,s.indexOf(':'))+", "+s.substring(s.indexOf(":")+1,s.indexOf('*')));
        }
        listHist.add("RETOUR");
        adaptaterHist.notifyDataSetChanged();
        layout.setVisibility(View.GONE);
        hist.setVisibility(View.VISIBLE);

        hist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < listHist.size() - 1) {
                    int i = -1, j = -1;
                    String s3 = s2;
                    while (i < position) {
                        j = s3.indexOf('*', j + 1);
                        i++;
                    }//j est alors l'index de l'élèment à enlever
                    int k = s3.indexOf('*', j + 1) == -1 ? s2.length() : s3.indexOf('*', j + 1);//la partie à enlever se trouve entre j et k
                    if (i > -1) {
                        try {
                            OutputStreamWriter out = new OutputStreamWriter(openFileOutput("registre.txt", MODE_PRIVATE));
                            s3 = s2.substring(0, j) + s2.substring(k);
                            out.write(s3);
                            out.close();
                            Toast.makeText(getApplicationContext(), "Supprimé " + s2.substring(j, k), Toast.LENGTH_SHORT).show();
                        } catch (FileNotFoundException e) {
                            Toast.makeText(getApplicationContext(), "impossible d'ouvrir le fichier", Toast.LENGTH_SHORT);
                        } catch (IOException e) {
                            Toast.makeText(getApplicationContext(), "impossible d'ecrire", Toast.LENGTH_SHORT);
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "Rien à effacer!", Toast.LENGTH_SHORT).show();
                    }
                }
                info.setText(getlastlog("registre.txt"));
                hist.setVisibility(View.GONE);
                layout.setVisibility(View.VISIBLE);
            }
        });

    }

    @Override
    public void onBackPressed() {
        if(hist.getVisibility()==View.GONE)
            super.onBackPressed();
        else{
            hist.setVisibility(View.GONE);
            layout.setVisibility(View.VISIBLE);
        }
    }
}
