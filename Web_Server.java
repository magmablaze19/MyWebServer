import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;
import java.util.ArrayList;

public class MyWebServer {
    public static void main (String[] args) {
        ArrayList<String> request = new ArrayList<String>();
        ServerSocket svrSct;
        Socket sct;
        InputStream input = null;
        OutputStream output = null;
        System.out.println("Goober");
        String port = args[0];
        String root_path = args[1];
        try{
        svrSct = new ServerSocket(Integer.parseInt(port));
        sct = svrSct.accept();
        input = sct.getInputStream();
        output = sct.getOutputStream();
        }
        catch (Exception e){
            System.out.println(e);
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            int i = 0;
            while (i != -1) {
                String temp = reader.readLine();
                if (!temp.isEmpty()){
                    request.add(temp);
                    i++;
                }
                else{
                    i = -1;
                }
            }
            System.out.println(request.toString());

            //Open File and confirm it exists
            System.out.println("opening "+ root_path + getPath(request.get(0)));
            File file = OpenFile(root_path + getPath(request.get(0)));
            if (file == null){
                System.out.println("File is Empty");
                String httprsp = "HTTP/1.1 404 File Not Found\r\n";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
                return;
            }
            else{
                System.out.println("File Retreval Succeded");
            }
            
            //Create Formatter to format attributes in HTTP date time format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM d hh:mm:ss zzz yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

            //Create Attributes Object to pull file attributes for response
            Path path = Paths.get(root_path + getPath(request.get(0))); 
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class); 
            
            //Check for header, and if present check if file has been modified since.
            if (checkForIMSHeader(request,attributes.lastModifiedTime().toInstant().toEpochMilli())){
                String httprsp = "HTTP/1.1 304 Not Modified\r\n";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
                return;
            }

            Integer responseType = checkForHeadorGet(request);
            if (responseType == 1){
                //Send Response
                String httprsp = "HTTP/1.1 200 OK\r\n"
                + "Date: " + LocalDateTime.now().format(formatter) + "\n"
                + "Server: Alex's Server\n"
                + "Last-Modified: " + formatter.format(attributes.lastModifiedTime().toInstant()) + "\n"
                + "Content-Length: " + Long.toString(attributes.size()) + "\n"
                + "\r\n";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
            }
            if (responseType == 2){
                //Send Response and requested file
                String httprsp = "HTTP/1.1 200 OK\r\n"
                + "Date: " + LocalDateTime.now().format(formatter) + "\n"
                + "Server: Alex's Server\n"
                + "Last-Modified: " + formatter.format(attributes.lastModifiedTime().toInstant()) + "\n"
                + "Content-Length: " + Long.toString(attributes.size()) + "\n"
                + "\r\n";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 

                Scanner scan = new Scanner(file);
                String fileString = scan.useDelimiter("\\Z").next();
                scan.close();
                output.write(fileString.getBytes("UTF-8"));
                output.flush();


            }
            if (responseType == 0){
                String httprsp = "HTTP/1.1 501 Not Implemented\r\n";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
            }


        }
        catch (Exception e){
            String httprsp = "HTTP/1.1 400 Bad Request\r\n";
            try {
            output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
            output.flush();
            }
            catch (Exception f){
                System.out.println(f);
            }
            System.out.println(e);
        }
    }

    public static File OpenFile(String path){
        File file = new File(path);
        if (file.getAbsoluteFile().exists()){
            return file;
        }
        else{
            return null;
        }
    }

    public static String getPath(String string){
        String[] result = string.split("\\s");
        if (result[1].equals("/")){
            return "/index.html";
        }
        return result[1];
    }

    public static Boolean checkForIMSHeader(ArrayList<String> req, long lastModifiedTime){
        for (int i = 0; i < req.size(); i++){
            String temp1 = req.get(i);
            String[] temp2 = temp1.split("\\s");
            if (temp2[0].equals("If-Modified-Since:") || temp2[0].equals(" If-Modified-Since:")){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM d hh:mm:ss zzz yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);
                String date = temp2[1] + " " + temp2[2] + " " + temp2[3] + " " + temp2[4] + " " + temp2[5] + " " + temp2[6];
                System.out.println(date);
                ZonedDateTime zdt = ZonedDateTime.parse(date, formatter);
                long reqtime = zdt.toInstant().toEpochMilli();
                System.out.println("File LMT: "+Long.toString(lastModifiedTime) + "Request LMT: "+ Long.toString(reqtime));
                if(lastModifiedTime>reqtime){
                    return false;
                }
                else{
                    return true;
                }
            }
        }
        return false;
    } 

    public static Integer checkForHeadorGet(ArrayList<String> req){
        for (int i = 0; i < req.size(); i++){
            String temp1 = req.get(i);
            String[] temp2 = temp1.split("\\s");
            if (temp2[0].equals("HEAD")){
                return 1;
            }
            if (temp2[0].equals("GET")){
                return 2;
            }
        }
        return 0;
    }
}
