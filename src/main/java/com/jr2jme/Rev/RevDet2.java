package com.jr2jme.Rev;

import com.jr2jme.st.UnBzip2;
import com.mongodb.*;
import net.java.sen.SenFactory;
import net.java.sen.StringTagger;
import net.java.sen.dictionary.Token;
import net.java.sen.filter.stream.CompositeTokenFilter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

//import org.atilika.kuromoji.Token;

//import net.java.sen.dictionary.Token;

//import org.atilika.kuromoji.Token;


public class RevDet2 {//Wikipediaのログから差分をとって誰がどこを書いたかを保存するもの リバート対応
    private static DBCollection coll;
    //private static JacksonDBCollection<WhoWrite,String> coll2;
    //private static JacksonDBCollection<InsertedTerms,String> coll3;//insert
    //private static JacksonDBCollection<DeletedTerms,String> coll4;//del&
    //private String wikititle = null;//タイトル
    static DB db=null;
    public static void main(String[] args){
       // Set<String> aiming=fileRead("input.txt");
        MongoClient mongo=null;
        try {
            mongo = new MongoClient("dragons.db.ss.is.nagoya-u.ac.jp",27017);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        assert mongo != null;
        DB db=mongo.getDB("wikipediaDB_kondou");
        DBCollection dbCollection=db.getCollection("wikitext_Islam");
        coll=db.getCollection("wikitext_Islam");
        //coll = JacksonDBCollection.wrap(dbCollection, Wikitext.class, String.class);
        DBCollection dbCollection2=db.getCollection("editor_term_Islam");
        DBCollection dbCollection3=db.getCollection("Insertedterms_Islam");
        DBCollection dbCollection4=db.getCollection("DeletedTerms_Islam");

        //coll2 = JacksonDBCollection.wrap(dbCollection2, WhoWrite.class,String.class);
        //coll3 = JacksonDBCollection.wrap(dbCollection3, InsertedTerms.class,String.class);
        //coll4 = JacksonDBCollection.wrap(dbCollection4, DeletedTerms.class,String.class);
        Set<String> AimingArticle = fileRead("input.txt");
        XMLStreamReader reader = null;
        BufferedInputStream stream = null;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        try {
            reader = factory.createXMLStreamReader(UnBzip2.unbzip2(args[0]));
            // 4. イベントループ
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Boolean inrev = false;
            Boolean incon = false;
            String comment = "";
            String title = "";
            String name = "";
            Date date = null;
            String text;

            int version = 0;
            int id = 0;
            Boolean isAimingArticle = false;
            assert reader != null;
            List<String> prev_text = new ArrayList<String>();
            int tail=0;
            int head;
            List<WhoWrite> prevdata = null;
            Map<String,List<DelPos>> delmap = new HashMap<String, List<DelPos>>();
            List<List<String>> difflist = new ArrayList<List<String>>();
            WhoWrite prevwrite=new WhoWrite();
            while(reader.hasNext()) {
                // 4.1 次のイベントを取得
                int eventType = reader.next();
                // 4.2 イベントが要素の開始であれば、名前を出力する
                if (eventType == XMLStreamReader.START_ELEMENT) {
                    if ("title".equals(reader.getName().getLocalPart())) {
                        //System.out.println(reader.getElementText());
                        title = reader.getElementText();
                        //System.out.println(title);
                        if (true) {/*AimingArticle.contains(title)*/
                            isAimingArticle = true;
                            version = 0;
                            prevdata = null;
                            tail = 0;
                            prev_text = new ArrayList<String>();
                            delmap = new HashMap<String, List<DelPos>>();
                            difflist = new ArrayList<List<String>>();
                            //System.out.println(reader.getElementText());
                        } else {
                            //System.out.println(reader.getElementText());
                            isAimingArticle = false;
                        }

                    }
                    if (isAimingArticle) {
                        if ("revision".equals(reader.getName().getLocalPart())) {
                            inrev = true;
                        }
                        if ("id".equals(reader.getName().getLocalPart())) {
                            if (inrev && !incon) {
                                id = Integer.valueOf(reader.getElementText());
                            }
                        }
                        if ("comment".equals(reader.getName().getLocalPart())) {
                            comment = reader.getElementText();

                        }
                        if ("timestamp".equals(reader.getName().getLocalPart())) {
                            //System.out.println(reader.getElementText());
                            try {
                                date = sdf.parse(reader.getElementText());
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }

                        if ("ip".equals(reader.getName().getLocalPart())) {
                            //System.out.println(reader.getElementText());
                            name = reader.getElementText();
                            incon = true;
                        }
                        if ("username".equals(reader.getName().getLocalPart())) {
                            //System.out.println(reader.getElementText());
                            name = reader.getElementText();
                            incon = true;
                        }
                        if ("text".equals(reader.getName().getLocalPart())) {

                            text=reader.getElementText();
                            //System.out.println(text);
                            //List<Future<List<String>>> futurelist = new ArrayList<Future<List<String>>>(NUMBER+1);
                            StringTagger tagger = SenFactory.getStringTagger(null);
                            CompositeTokenFilter ctFilter = new CompositeTokenFilter();

                            try {
                                ctFilter.readRules(new BufferedReader(new StringReader("名詞-数")));
                                tagger.addFilter(ctFilter);

                                ctFilter.readRules(new BufferedReader(new StringReader("記号-アルファベット")));
                                tagger.addFilter(ctFilter);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            List<Token> tokens = new ArrayList<Token>();
                            try {
                                tokens=tagger.analyze(text, tokens);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            List<String> current_text = new ArrayList<String>(tokens.size());

                            for(Token token:tokens){

                                current_text.add(token.getSurface());
                            }
                            int i=0;
                            Levenshtein3 d = new Levenshtein3();
                            List<String> diff = d.diff(prev_text, current_text);
                            i=0;
                            int c = 0;
                            List<InsTerm> instermlist = new ArrayList<InsTerm>();
                            int tmp = 0;
                            WhoWrite whowrite = new WhoWrite();
                            int a = 0;
                            int b = 0;
                            List<String> yoyaku = new ArrayList<String>();
                            List<String> yoyakued = new ArrayList<String>();
                            List<Integer> yoyakuver = new ArrayList<Integer>();
                            List<String> edlist = new ArrayList<String>();
                            //System.out.println(diff);
                            for (String type : diff) {
                                if (type.equals("+")) {
                                    edlist.add(name);
                                    whowrite.add(current_text.get(a),name,version);
                                    instermlist.add(new InsTerm(current_text.get(a), a, name));
                                    a++;
                                } else if (type.equals("-")) {
                                    yoyakued.add(prevwrite.getEditorList().get(b));
                                    yoyakuver.add(prevwrite.getVerlist().get(b));
                                    yoyaku.add(prev_text.get(b));
                                    //System.out.println(prev_text.get(b));//リバートされるかもしれないリストに突っ込む準備
                                    //delterm.add(futurelist.get(c).get().get(a));
                                    //whowrite.delete(b,version);//追加した単語には位置とかいろいろ情報あって分かるので適当にやる
                                    b++;
                                } else if (type.equals("|")) {
                                    for (int p = 0; p < yoyaku.size(); p++) {
                                        if (delmap.containsKey(yoyaku.get(p))) {
                                            List<DelPos> list = delmap.get(yoyaku.get(p));
                                            DelPos pos = new DelPos(version, tmp, b, name, yoyakuver.get(p), yoyakued.get(p));
                                            list.add(pos);
                                        } else {
                                            List<DelPos> list = new ArrayList<DelPos>();
                                            DelPos pos = new DelPos(version, tmp, b, name, yoyakuver.get(p), yoyakued.get(p));
                                            list.add(pos);
                                            delmap.put(yoyaku.get(p), list);
                                        }
                                    }
                                    whowrite.add(prevwrite.getWikitext().get(b),prevwrite.getEditorList().get(b),prevwrite.getVerlist().get(b));
                                    tmp = b;
                                    a++;
                                    b++;
                                }

                            }

                            prev_text=current_text;

                            prevwrite = whowrite;
                            for (InsTerm term : instermlist) {//今追加した単語が
                                for (Map.Entry<String, List<DelPos>> del : delmap.entrySet()) {//消されたものだったか
                                    if (del.getKey().equals(term.getTerm())) {//確かめて
                                        for (DelPos delpos : del.getValue()) {
                                            int ue = delpos.getue();//文章の上と
                                            int shita = delpos.getshita();//下で
                                            int tmpue = delpos.getue();
                                            int tmpshita = delpos.getshita();
                                            int preue = 0;
                                            for (int x = version - delpos.getVersion(); x < version; x++) {//矛盾が出ないか確かめる
                                                a = 0;
                                                b = 0;
                                                for (int y = 0; y < difflist.get(x).size(); y++) {
                                                    String type = difflist.get(x).get(y);
                                                    if (type.equals("+")) {
                                                        tmpue++;
                                                        tmpshita++;
                                                        a++;
                                                    } else if (type.equals("-")) {
                                                        b++;
                                                        tmpue--;
                                                        tmpshita--;
                                                    } else if (type.equals("|")) {
                                                        if (b <= preue) {
                                                            ue = tmpue;
                                                        }
                                                        shita = tmpshita;
                                                        if (a > shita) {
                                                            break;//?
                                                        }
                                                        a++;
                                                        b++;
                                                    }
                                                }
                                                preue = ue;
                                            }

                                            if (term.pos > ue && term.pos < shita) {
                                                term.revertterm(delpos);
                                                whowrite.revert(term.getPos(), delpos.deledver, delpos.getDelededitor());
                                                System.out.println("delrev");
                                                del.getValue().remove(delpos);
                                                break;
                                            }
                                        }
                                        System.out.println(term.getTerm());
                                    }
                                }
                                difflist.add(diff);
                            }
                        }

                    }
                }
            }
        } catch (IOException ex) {
            System.err.println(ex + " が見つかりません");
        } catch (XMLStreamException ex) {
            System.err.println(ex + " の読み込みに失敗しました");
        } finally {
            // 5. パーサ、ストリームのクローズ
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ex) {
                    ex.printStackTrace();
                }
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            mongo.close();
        }
        /*RevDet wikidiff=new RevDet();
        //wikititle= title;//タイトル取得
        //Pattern pattern = Pattern.compile(title+"/log.+|"+title+"/history.+");
        Cursor cur=null;
        cur=wikidiff.wikidiff("アクバル");
        cur.close();
        mongo.close();
        System.out.println("終了:"+arg[0]);*/

    }

    public static Set fileRead(String filePath) {

        FileReader fr = null;
        BufferedReader br = null;
        Set<String> aiming= new HashSet<String>(350);
        try {
            fr = new FileReader(filePath);
            br = new BufferedReader(fr);

            String line;
            while ((line = br.readLine()) != null) {
                //System.out.println(line);
                aiming.add(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return aiming;
    }
}

class DiffPos {
    List<String> del;
    List<String> insert;
    int preue;
    int preshita;
    int nowue;
    int nowshita;
    public DiffPos(List<String> insert, List<String> del, int preue, int preshita, int nowue, int nowshita){
        this.del=del;
        this.insert=insert;
        this.preue=preue;
        this.preshita=preshita;
        this.nowue=nowue;
        this.nowshita=nowshita;
    }
}

class Kaiseki implements Callable<List<String>> {//形態素解析
    String wikitext;//gosenだとなんか駄目だった→kuromojimo別のでダメ
    public Kaiseki(String wikitext){
        this.wikitext=wikitext;
    }
    @Override
    public List<String> call() {

        StringTagger tagger = SenFactory.getStringTagger(null);
        CompositeTokenFilter ctFilter = new CompositeTokenFilter();

        try {
            ctFilter.readRules(new BufferedReader(new StringReader("名詞-数")));
            tagger.addFilter(ctFilter);

            ctFilter.readRules(new BufferedReader(new StringReader("記号-アルファベット")));
            tagger.addFilter(ctFilter);
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<Token> tokens = new ArrayList<Token>();
        try {
            tokens=tagger.analyze(wikitext, tokens);
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> current_text = new ArrayList<String>(tokens.size());

        for(Token token:tokens){

            current_text.add(token.getSurface());
        }

        return current_text;
    }


}

class CalDiff implements Callable<List<String>> {//差分
    List<String> current_text;
    List<String> prev_text;
    public CalDiff(List<String> current_text,List<String> prev_text,String title,int version,String name){
        this.current_text=current_text;
        this.prev_text=prev_text;
    }
    @Override
    public List<String> call() {//並列で差分
        Levenshtein3 d = new Levenshtein3();
        List<String> diff = d.diff(prev_text, current_text);
        return diff;
    }
}

class InsTerm {
    String term;
    String editor;
    int pos;
    Boolean isRevert=false;
    Integer revedver=null;
    String reveded;
    public InsTerm(String term,int pos,String editor){
        this.term=term;
        this.pos=pos;
    }
    public void revertterm(DelPos delpos){
        isRevert=true;
        revedver=delpos.getVersion();
        reveded=delpos.getEditor();
    }

    public String getTerm() {
        return term;
    }

    public int getPos() {
        return pos;
    }
}

class Delterm{
    String term;
    String editor;
    int revedver=0;
    String reveded;
}

class DelPos{
    int ue;
    int shita;
    int version;
    String editor;
    int deledver;
    String delededitor;
    public DelPos(int version,int ue,int shita,String editor,int deledver,String delededitor){
        this.ue=ue;
        this.shita=shita;
        this.version=version;
        this.delededitor=delededitor;
        this.editor=editor;
        this.deledver=deledver;
    }
    public int getue() {
        return ue;
    }

    public int getshita() {
        return shita;
    }

    public int getVersion() {
        return version;
    }

    public String getEditor() {
        return editor;
    }

    public int getDeledver() {
        return deledver;
    }

    public String getDelededitor() {
        return delededitor;
    }
}

class WhoWrite{
    List<String> wikitext=new ArrayList<String>();
    List<String> editorList=new ArrayList<String>();
    List<Integer> verlist= new ArrayList<Integer>();
    /*public void delete(int pos,String editor,int version){
        wikitext.remove(pos);
        editorList.remove(pos);
        verlist.remove(pos);
    }*/
    public void add(String term,String editor,int ver){
        wikitext.add(term);
        editorList.add(editor);
        verlist.add(ver);
    }

    public List<Integer> getVerlist() {
        return verlist;
    }

    public List<String> getEditorList() {
        return editorList;
    }
    public void revert(int pos,int revver,String reved){
        editorList.set(pos,reved);
        verlist.set(pos,revver);
    }

    public List<String> getWikitext() {
        return wikitext;
    }
}