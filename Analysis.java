package analysis;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import org.json.simple.*;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Scanner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.json.simple.parser.JSONParser;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class Analysis {

   
    static ArrayList<String> appid = new ArrayList();
    static ArrayList<String> setPrice = new ArrayList();
    static ArrayList<String> singleGame = new ArrayList();
    static String cookie;
    static ArrayList<String> gamename = new ArrayList();
    static Double price;
    static int acceptableQuantity;
    static Logger log = Logger.getLogger(Analysis.class.getName());
    static String log4jConfPath = "log4j.properties";

    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure(log4jConfPath);
        Scanner sc = new Scanner(System.in);
        log.info("Enter cookie");
        cookie = sc.nextLine();
        log.info("Enter set price");
        price = sc.nextDouble();
        log.info("Enter quantity per week");
        acceptableQuantity = sc.nextInt();
        getCSV();
        getCardLists();
        log.info("Press enter to exit");
        System.in.read();

    }

    @SuppressWarnings({"CallToPrintStackTrace", "SleepWhileInLoop"})
    public static void getCardLists() throws Exception {
        int totalGames=1;
        int totalQuantityPerGame;
        PrintWriter pw = new PrintWriter(
                new BufferedWriter(
                        new FileWriter("Profit.txt", true)
                ), true
        );
        Iterator iter = gamename.iterator();
        for (String i : appid) {
            log.info("(" + totalGames + "\\" + appid.size() + ")");
            int retries = 0;
            if (retries < 3) {
                try {

                    Document doc = Jsoup.connect("https://steamcommunity.com/market/search?category_753_Game%5B%5D=tag_app_" + i + "&category_753_cardborder%5B%5D=tag_cardborder_0&category_753_item_class%5B%5D=tag_item_class_2&appid=753").get();

                    Elements card_names = doc.getElementsByClass("market_listing_item_name");
                    card_names.forEach(element -> singleGame.add(element.text()));

                    

                    totalQuantityPerGame = getPriceHistory(i);
                    singleGame.clear();

                    Thread.sleep(1000);
                    if (totalQuantityPerGame >= acceptableQuantity || totalQuantityPerGame == 0) {
                        String game = iter.next().toString();
                        log.info(game);
                        log.info("Appid :" + i);
                        log.info("Total Quantity Sold = " + totalQuantityPerGame);
                        log.info("Status : Accepted");
                        pw.println(game + "-" +i);
                    } else {
                        log.info(iter.next());
                        log.info("Appid :" + i);
                        log.info("Total Quantity Sold = " + totalQuantityPerGame);
                        log.info("Status : Ignored");
                    }

                } catch (HttpStatusException http) {
                    if (http.getStatusCode() == 429) {
                        log.warn("Too many requests,waiting upto 4 minutes");
                        Thread.sleep(240000);
                        iter.next();
                        retries++;
                    }

                }
            }
            totalGames++;
        }

    }

    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    static public ArrayList<String> getCSV() {
        try {

            Reader in = new FileReader("game.csv");
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in);
           
            for (CSVRecord record : records) {
                if (Double.parseDouble(record.get(5).replace(",",""))>= price) {
                    appid.add(record.get(15));
                    gamename.add(record.get(0));
                    setPrice.add(record.get(5));
                }
               
            }       
        } catch (Exception e) {
            e.printStackTrace();
        }
        return appid;
    }

    @SuppressWarnings({"SleepWhileInLoop", "CallToPrintStackTrace"})
    static public int getPriceHistory(String currentAppid) throws Exception {
        int currentQuantity = 0;

        for (String i : singleGame) {
            try {

                String priceLink = "https://steamcommunity.com/market/pricehistory/?country=US&currency=1&appid=753&market_hash_name=";

                String pricehistory = Jsoup.connect(priceLink + currentAppid + "-" + i.replace("&","%26").replace(" ", "%20").replace(",","%2C").replace("#","%23")).cookie("steamLoginSecure", cookie).ignoreContentType(true).execute().body();

                currentQuantity += analyze(pricehistory);

                Thread.sleep(1000);
                
            } catch (HttpStatusException http) {
                if (http.getStatusCode() == 500) {
                    log.warn("Internal Server Error, cannot get price history for " + i);
                }

            }
        }

        return currentQuantity;
    }

    static public int analyze(String pricehistory) throws Exception {
        int totalQuantityPerGame = 0;
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd yyyy");

        Long dayDifference;
        Date todayDate = new Date();
        Date soldDate;

        JSONParser parser = new JSONParser();
        JSONObject prices = (JSONObject) parser.parse(pricehistory);

        JSONArray array = (JSONArray) prices.get("prices");

        for (Object o : array) {
            JSONArray k = (JSONArray) o;
            String dateInString = (String) k.get(0);
            soldDate = formatter.parse(dateInString);

            dayDifference = todayDate.getTime() - soldDate.getTime();
            dayDifference = dayDifference / (1000 * 60 * 60 * 24);
            totalQuantityPerGame = totalQuantityPerGame + quantitySold(dayDifference, k);

        }
        return totalQuantityPerGame;

    }

    static public int quantitySold(Long dayDifference, JSONArray k) {
        int quantitySold = 0;
        String currentQuantity;
        if (dayDifference <= 7) {

            currentQuantity = (String) (k.get(2));
            quantitySold = quantitySold + Integer.parseInt(currentQuantity);

        }

        return quantitySold;
    }

}
