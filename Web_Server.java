//Alexandre Passin
//MyWebServer project for Network Systems and Design

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.ArrayList;

public class MyWebServer {
    public static void main (String[] args) {
        //Initialize Essential Variables
        ServerSocket svrSct = null;
        Socket sct;
        String port = args[0];
        String root_path = args[1];
        try{
            //Init socket
            svrSct = new ServerSocket(Integer.parseInt(port));
        }
        catch (Exception e){
            System.out.println(e);
        }
        while(svrSct != null){
            try {
                //Constantly poll for requests on the socket, and process them when they arrive.
                sct = svrSct.accept();
                processRequest(sct, root_path);
            }
            catch (Exception e){
                System.out.println(e);
            }
        }
    }

    public static void processRequest(Socket sct, String root_path){
        //Init essenial vars
        ArrayList<String> request = new ArrayList<String>();
        InputStream input = null;
        OutputStream output = null;
        try{
            //Create input and output streams
            input = sct.getInputStream();
            output = sct.getOutputStream();
        }
        catch (Exception e){
            System.out.println(e);
        }
            try{
                //Create and read data from buffered reader
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
            //Print Incoming Request to Terminal
            System.out.println(request.toString());
            //Open File and confirm it exists
            File file = OpenFile(root_path + getPath(request.get(0)));
            if (file == null){
                //If File is not found, return appropriate error and close streams.
                String httprsp = "HTTP/1.1 404 File Not Found\r\n\r\n" + "<h1>Error 404: File Not Found</h1>";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
                output.close();
                input.close();
                return;
            }
            
            //Create Formatter to format attributes in HTTP date time format
            SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy");
            formatter.setTimeZone(TimeZone.getTimeZone("EST"));

            //Create Attributes Object to pull file attributes for response
            Path path = Paths.get(root_path + getPath(request.get(0))); 
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class); 
            
            //Check for header, and if present check if file has been modified since.
            if (checkForIMSHeader(request,attributes.lastModifiedTime().toInstant().toEpochMilli())){
                //If not modified, return expected error.
                String httprsp = "HTTP/1.1 304 Not Modified\r\n\r\n" + "<h1>Error 304: Not Modified</h1>";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
                output.close();
                input.close();
                return;
            }
            //Check for request type
            Integer responseType = checkForHeadorGet(request);
            Date currdate = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
            if (responseType == 1){
                //Send Header for POST request
                String httprsp = "HTTP/1.1 200 OK\r\n"
                + "Date: " + formatter.format(currdate) + "\n"
                + "Server: Alex's Server\n"
                + "Last-Modified: " + formatter.format(new Date(attributes.lastModifiedTime().toMillis())) + "\n"
                + "Content-Length: " + Long.toString(attributes.size()) + "\n"
                + "\r\n";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
                output.close();
                input.close();
            }
            if (responseType == 2){
                //Send Response and requested file for GET request
                String httprsp = "HTTP/1.1 200 OK\r\n"
                + "Date: " + formatter.format(currdate) + "\n"
                + "Server: Alex's Server\n"
                + "Last-Modified: " + formatter.format(new Date(attributes.lastModifiedTime().toMillis())) + "\n"
                + "Content-Length: " + Long.toString(attributes.size()) + "\n"
                + "\r\n";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                //Convert file to byte data and stream to client
                byte[] fileBytes = new byte[(int) file.length()];
                try (FileInputStream finputstream = new FileInputStream(file)) {
                    finputstream.read(fileBytes);
                  }
                output.write(fileBytes);
                output.flush();
                output.close();
                input.close();
            }
            if (responseType == 0){
                //Send error for unimplemented function. Ocasionally only the http is displayed in curl, unsure why.
                //Code is identical to other error message sending code.
                String httprsp = "HTTP/1.1 501 Not Implemented\r\n\r\n" + "<h1>Error 501: Not Implemented</h1>";
                output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
                output.flush();
                output.close();
                input.close();
            }


        }
        catch (Exception e){
            //Send error if program would have crashed due to bad formatted request.
            String httprsp = "HTTP/1.1 400 Bad Request\r\n\r\n" + "<h1>Error 400: Bad Request</h1>";
            try {
            output.write(httprsp.getBytes(StandardCharsets.UTF_8)); 
            output.flush();
            output.close();
            input.close();
            }
            catch (Exception f){
                System.out.println(f);
            }
            System.out.println(e);
        }
    }


    public static File OpenFile(String path){
        //Open the file at the requested path, return the file if it exists and don't if it doesnt
        File file = new File(path);
        if (file.getAbsoluteFile().exists()){
            return file;
        }
        else{
            return null;
        }
    }

    public static String getPath(String string){
        //Extract the filepath from the http request
        String[] result = string.split("\\s");
        if (result[1].equals("/") || result[1].charAt(result[1].length()-1) == '/'){
            return result[1]+"index.html";
        }
        return result[1];
    }

    public static Boolean checkForIMSHeader(ArrayList<String> req, long lastModifiedTime){
        //Check for the If-Modified-Since header, if present check if file has been modified since the given date
        for (int i = 0; i < req.size(); i++){
            String temp1 = req.get(i);
            String[] temp2 = temp1.split("\\s");
            if (temp2[0].equals("If-Modified-Since:") || temp2[0].equals(" If-Modified-Since:")){
                SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
                formatter.setTimeZone(TimeZone.getTimeZone("EST"));
                //Combine split string into date
                String date = temp2[1] + " " + temp2[2] + " " + temp2[3] + " " + temp2[4] + " " + temp2[5] + " " + temp2[6];
                System.out.println(date);
                Date zdt = null;
                try {
                    zdt = formatter.parse(date);
                }
                catch (Exception e){
                    System.out.println("please");
                }
                long reqtime = zdt.toInstant().toEpochMilli();
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
        //Check request for type of request, and return 1 for HEAD, 2 for GET, and 0 for neither.
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
