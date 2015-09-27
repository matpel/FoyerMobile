package com.matpel.foyermobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;


public class MainActivity extends Activity {

    private static Vector<String> listNoms = new Vector<>(0);//Vector contenant les noms de TOUT les élèves
    private static Vector<String> listMails = new Vector<>(0);//Vector contenant les noms de TOUT les élèves
    private static Vector<String> listIdNom = new Vector<>(0);//Vector contenant les identifiants de tout les élèves, dans le même ordreque listNoms (par ex. peluchom pour Mathias Peluchon)
    private static Vector<String> listConso = new Vector<>(0);//Vector contenant les noms de toutes les consos
    private static Vector<String> listIdConsos = new Vector<>(0);//idem que pour les noms des élèves
    private static Vector<Double> listSoldes = new Vector<>(0);//vector contnant les soldes de tout les élèves
    private static Vector<Double> listPrix = new Vector<>(0);//vector contenant les prix des bières
    ArrayAdapter<String> adaptaterNoms = null;//adaptater permettant de peuplet l'autocomplete des noms
    ArrayAdapter<String> adaptaterConso = null;//idem pour les consos
    ArrayAdapter<String> adaptaterHist = null;//idem pour le listView de l'historique
    //Lorsqu'on sélectionne une conso, le clavier se rétracte tout seul. Ce n'est pas la cas pour les noms car on en a encore besoin pour soit entrer un autre nom, soit entrer une conso
    AdapterView.OnItemClickListener consoItemListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            InputMethodManager in = (InputMethodManager) getSystemService(MainActivity.INPUT_METHOD_SERVICE);
            in.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
        }
    };
    private  URL url_site;
    private String token; //token fourni par l'API uPont suite à la requête /login/username/id/password/mdp
    private int code=0;//code erreur retourné par l'API
    private ListView hist=null; //historique des consos stockées sur le mobile
    private RelativeLayout layout = null;
    private MultiAutoCompleteTextView nom = null; //le TextView permettant d'entrer les (les) nom(s) de(s) élève(s) pour enregistrer une conso
    private AutoCompleteTextView conso = null;//champ permettant d'entrer UNE conso pour tout les élèves inscrits dans le champ nom
    private TextView info = null;//textview indiquant la dernière entrée (géré par getlastlog())
    //action du bouton ok, on écrit la/les commandes dans le fichier registre.txt en checkant que l'élève et la conso existent bien.
    //le format d'écriture est *nom1:conso1*nom2:conso2
    View.OnClickListener okListener = new View.OnClickListener() {
        //on écrit la/les conso(s) dans le fichier registre.txt
        @Override
        public void onClick(View v) {
            try {
                OutputStreamWriter out = new OutputStreamWriter(openFileOutput("registre.txt", MODE_APPEND));

                boolean bnom = false;
                boolean bconso = false;
                String string = nom.getText().toString();
                String multiNoms[] = string.substring(0, string.length() - 2).split(", ");//Le MultiAutocomplete rajoute un ", " à la fin de chaque nom, il faut l'éliminer, puis on sépare les noms
                for (String j : multiNoms) {
                    for (String i : listNoms)
                        bnom = bnom || (i.equals(j));
                    for (String i : listConso)
                        bconso = bconso || (i.equals(conso.getText().toString()));
                    if (bconso && bnom) {
                        out.write("*" + j + ":" + conso.getText().toString());
                        Toast.makeText(getApplicationContext(), "OK !", Toast.LENGTH_SHORT).show();
                    } else if (bconso)
                        Toast.makeText(getApplicationContext(), "Conso inexistante: " + conso.getText().toString(), Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(getApplicationContext(), "Mauvais nom/prénom: " + j, Toast.LENGTH_SHORT).show();
                }
                out.close();
            } catch (java.io.IOException e) {
                Toast.makeText(getApplicationContext(), "Impossible d'ecrire dans le fichier", Toast.LENGTH_SHORT).show();
            }
            info.setText(getlastlog("registre.txt"));//on met à jour la ligne qui informe de la dernière conso entrée

        }
    };
    private TextView solde = null;//champ indiquant le solde du dernier élève entré dans le champ nom
    //Lorsqu'on sélectionne un nom dans la liste déroulante du EditText nom, on affiche le solde du nom sélectionné
    AutoCompleteTextView.OnDismissListener nomDismissListener = new AutoCompleteTextView.OnDismissListener() {
        @Override
        public void onDismiss() {
            try {
                String nomString = nom.getText().toString();
                String nomEntresArray[] = nomString.substring(0, nomString.length() - 2).split(", ");
                Double s = listSoldes.elementAt(listNoms.indexOf(nomEntresArray[nomEntresArray.length - 1]));//on inscrit le solde du dernier entré
                String s_string = s.toString();
                solde.setText("" + s_string.substring(0, s_string.indexOf(".") + 2) + "\u20AC");
            } catch (IndexOutOfBoundsException e) {
                if (nom.getText().toString().length() > 0)
                    Toast.makeText(getApplicationContext(), "Erreur lecture solde", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private TextView prix = null;//champ indiquant le prix de la bière entrée dans conso
    //idem que pour le Edittext nom, lorsqu'on a sélectionné une conso, on affiche son prix
    AutoCompleteTextView.OnDismissListener consoDismissListener = new AutoCompleteTextView.OnDismissListener() {
        @Override
        public void onDismiss() {
            try {
                Double s = listPrix.elementAt(listConso.indexOf(conso.getText().toString()));
                prix.setText("" + s + "\u20AC");
            } catch (IndexOutOfBoundsException e) {
                if (conso.getText().toString().length() > 0)
                    Toast.makeText(getApplicationContext(), "Erreur lecture prix", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private Button ok = null;
    private ImageButton param = null;
    private String id = null;
    private String mdp = null;
    private float lim_solde = 0;
    private Vector<String> listHist=new Vector<>(0);//vector contenant l'historique des entrées stockées sur le mobile

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            url_site = new URL("https://upont.enpc.fr");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        hist = (ListView) findViewById(R.id.list);
        layout = (RelativeLayout) findViewById(R.id.mainlayout);
        param = (ImageButton) findViewById(R.id.param);
        nom = (MultiAutoCompleteTextView) findViewById(R.id.Nomprenom);
        conso = (AutoCompleteTextView) findViewById(R.id.conso);
        ok = (Button) findViewById(R.id.boutonOk);
        info = (TextView) findViewById(R.id.info);
        solde = (TextView) findViewById(R.id.solde);
        prix = (TextView) findViewById(R.id.prix);
        info.setText(getlastlog("registre.txt"));
        nom.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        adaptaterNoms = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listNoms);
        adaptaterConso = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listConso);

        try {
            updateData(false);//on remplit les Vectors élèves/consos d'après la dernière sauvegarde effectuée sur le mobile dans clients.txt et stocks.txt (false: pas de synchronisation via l'API, ce n'est pas utile de le faire à chaque fois).
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //définition des Adaptaters peuplant les listes (historique, et listes déroulantes pour eleves/consos)
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
        conso.setOnItemClickListener(consoItemListener);

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR1) {
            //ce testest nécessaire pour définir le OnDismissListener plus bas
            //OnDismissListener pour AutoCompleteTextView n'existe que sur les API>=17
            nom.setOnDismissListener(nomDismissListener);
            conso.setOnDismissListener(consoDismissListener);
        }
        param.setOnClickListener(menuListener);
    }

    @Override //affichage du menu contextuel lors d'un appui prolongé sur le bouton paramètres
    public void onCreateContextMenu(ContextMenu m, View p, ContextMenu.ContextMenuInfo menuInfo) {
        //Création du contectmenu suite à l'appui long sur la roue dentée en haut à droite
        super.onCreateContextMenu(m, p, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, m);
    }

    View.OnClickListener menuListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final CharSequence[] items = {"Supprimer dernière entrée", "Historique", "Connexion", "Synchroniser", "MAJ clients/stocks", "Contacter les déficitaires", "!! Supprimer tout !!"};

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Menu");
            builder.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    menuHandleItem(item);
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    };

    private void menuHandleItem(int item) {
        switch (item) {
            case 0: {
                //si on veut supprimer la dernière entrée
                int i;
                String s = "";
                try {
                    InputStreamReader file = new InputStreamReader(openFileInput("registre.txt"));
                    BufferedReader buffreader = new BufferedReader(file);
                    String line;
                    try {
                        while ((line = buffreader.readLine()) != null)
                            s += line;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                i = s.lastIndexOf('*');
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
                return;
            }

            case 1: {//si on veut accéder à l'historique
                editHist();
                return;
            }

            case 2: {
                //si on veut se connecter à uPont
                getCred();
                return;
            }

            case 3: {
                //si on veut synchroniser registre.txt sur le site
                boolean b;
                if (token == null) {
                    Toast.makeText(getApplicationContext(), "Connecte-toi pour synchroniser", Toast.LENGTH_SHORT).show();
                    return;
                }
                    try {
                        InputStreamReader file = new InputStreamReader(openFileInput("registre.txt"));
                        BufferedReader buffreader = new BufferedReader(file);
                        String line, s = "";
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
                            int k1, k2;
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
                                return;
                            }
                            i = s.indexOf('*', i + 1);
                        }
                        Toast.makeText(getApplicationContext(), "Synchronisation terminée! Pense à tout supprimer!", Toast.LENGTH_SHORT).show();

                    } catch (FileNotFoundException e) {
                        Toast.makeText(getApplicationContext(), "Fichier registre.txt introuvable", Toast.LENGTH_SHORT).show();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                return;
            }

            case 4: {
                //si on veut récupérer les données de la BDD (noms+soldes+bières+prix) via l'API uPont
                if (id != null) {
                    try {
                        updateData(true);//on synchronise via l'API (true) la liste des élèves et celle des consos
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                return;
            }

            case 5: {
                //on envoie un mail à tout les déficitaires en dessous d'un seuil qu'on demande systématiquement
                sendMails();
                return;

            }
            case 6: {
                //si on veut tout supprimer
                (new AlertDialog.Builder(this).setTitle("Supprimer").setMessage("Veux-tu vraiment tout supprimer? (Irreversible)")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteFile("registre.txt");
                                Toast.makeText(getApplicationContext(), "Toutes les entrées ont été supprimées", Toast.LENGTH_SHORT).show();
                                info.setText("Aucune entrée");
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(getApplicationContext(), "Annulé!", Toast.LENGTH_SHORT).show();
                            }
                        })).show();
            }
        }
    }

    private void sendMails() {
        final float lim = 0;
        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        final View yourCustomView = inflater.inflate(R.layout.activity_limsolde, null);

        final TextView niveau = (EditText) yourCustomView.findViewById(R.id.limsolde);
        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Envoyer un mail à ceux dont le solde est inférieur à:")
                .setView(yourCustomView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        lim_solde = Float.valueOf(niveau.getText().toString());
                        Vector<String> listMailDeficit = new Vector<>();
                        int j = 0;
                        for (double i : listSoldes) {//on parcourt les soldes (i) et si i est en dessous de lim_solde, on va chercher le mail de l'utilisateur correspondant (au rang j dans le Vector).
                            if (i <= lim_solde)
                                listMailDeficit.add(listMails.get(j));
                            j++;
                        }
                        Log.d("test mail", listMailDeficit.toString());
                        Intent emailLauncher = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        emailLauncher.setType("message/rfc822");
                        emailLauncher.putExtra(Intent.EXTRA_EMAIL, listMailDeficit.toArray(new String[listMailDeficit.size()]));
                        emailLauncher.putExtra(Intent.EXTRA_SUBJECT, "[Foyer] Fin de la rigolade, il est temps de recharger!");
                        emailLauncher.putExtra(Intent.EXTRA_TEXT, "Salut,\n\nSi tu reçois ce mail, c'est parcequ'il est vraiment temps de recharger ton compte foyer. Tu fais partie des quelques élèves en dessous de " + lim_solde + " \u20ac...\n\nTu peux venir nous voir avec du liquide, un chèque, ou bien par Lydia/CB au lien suivant:\nhttps://lydia-app.com/collect/foyer-enpc-rechargement.\n\nTu peux aussi contacter Louise Chatelain pour obtenir un RIB.\n\nCordialement,\nLe Foyer 017");
                        try {
                            startActivity(emailLauncher);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getApplicationContext(), "Aucune application mail trouvée", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("Annuler", null).create();

        dialog.show();
    }

    private String getlastlog(String name) {
        //retourne la denrière entrée dans le registre
        String s = "Aucune entrée";
        try {
            InputStreamReader file = new InputStreamReader(openFileInput(name));
            BufferedReader buffreader = new BufferedReader(file);
            String line;
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
    }//pour obtenir la dernière entrée dans registre.txt

    private void updateData(boolean internet) throws JSONException {
        //met à jour les Vector concernant les consos et les noms. Si internet==true on fait appel à l'api, sinon on récupère juste les données sauvegarfées dans clients.txt et stocks.txt
        listConso.removeAllElements();
        listIdNom.removeAllElements();
        listIdConsos.removeAllElements();
        listPrix.removeAllElements();
        listNoms.removeAllElements();
        listMails.removeAllElements();
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
            Toast.makeText(getApplicationContext(), "Erreur ouverture fichier clients.txt. Faire une MAJ clients/stocks.", Toast.LENGTH_SHORT);
            return;
        }//s contient tout le fichier

        JSONArray json=new JSONArray(s);
        for(int i=0;i<json.length();i++){
            listNoms.add(json.getJSONObject(i).optString("first_name") + " " + json.getJSONObject(i).optString("last_name"));
            listMails.add(json.getJSONObject(i).optString("email"));
            listIdNom.add(json.getJSONObject(i).optString("username"));
            if (json.getJSONObject(i).has("balance"))
                listSoldes.add(json.getJSONObject(i).optDouble("balance"));
            else listSoldes.add(0.);
        }
        adaptaterNoms.notifyDataSetChanged();
        Toast.makeText(getApplicationContext(), "Liste des élèves à jour", Toast.LENGTH_SHORT).show();

        /////On met à jour les stocks après les clients
        if (internet && networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
            Get_products t = new Get_products(this,url_site,token);
            t.start();
            try {
                t.join();
                Toast.makeText(getApplicationContext(), "Stock téléchargé depuis le site", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(getApplicationContext(), "Erreur ouverture fichier stocks.txt. Faire une MAJ clients/stocks.", Toast.LENGTH_SHORT).show();
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
    }//pour obtenir les données de la BDD uPont

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
                                    int bytesRead;
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
    }//pour se connecter à uPont

    private void editHist(){
        listHist.removeAllElements();
        InputStreamReader file;
        try {
            file = new InputStreamReader(openFileInput("registre.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        BufferedReader buffreader = new BufferedReader(file);
        String line, s = "", strurl = "";
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
                listHist.remove(position);
                adaptaterHist.notifyDataSetChanged();
            }
        });

    }//pour supprimer des entrées dans l'historique

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
