package com.br.multicloudecore.awsmodule.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.br.multicloudecore.awsmodule.component.DynamicAWSCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Service
public class AwsS3Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsS3Service.class);
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "pdf", "txt");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB

    private final String bucketName;
    private AmazonS3 s3Client;

    private final DynamicAWSCredentialsProvider dynamicAWSCredentialsProvider;

    @Autowired
    public AwsS3Service(
            @Value("${spring.cloud.aws.s3.bucket-name}") String bucketName, DynamicAWSCredentialsProvider dynamicAWSCredentialsProvider
    ) {
        this.bucketName = bucketName;
        this.dynamicAWSCredentialsProvider = dynamicAWSCredentialsProvider;
        this.s3Client = AmazonS3ClientBuilder.standard().build();
        waitForVaultCredentials();
    }

      public void storeFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
          assert fileName != null;
          String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            LOGGER.error("File extension '{}' is not allowed for upload", extension);
            throw new IllegalArgumentException("File type not allowed");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            LOGGER.error("File size {} exceeds the maximum allowed size of {}", file.getSize(), MAX_FILE_SIZE);
            throw new IllegalArgumentException("File size exceeds the maximum allowed size");
        }

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            PutObjectRequest request = new PutObjectRequest(
                    bucketName,
                    file.getOriginalFilename(),
                    inputStream, metadata);

             s3Client.putObject(request);

        } catch (AmazonClientException e) {
            LOGGER.error("Error occurred during S3 file upload", e);
        } catch (IOException e) {
            LOGGER.error("Error occurred while reading file for S3 upload", e);
        }
    }

    public void uploadPhoto(BufferedImage image, String fileName) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", os);
        byte[] imageBytes = os.toByteArray();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);

        this.s3Client.putObject(bucketName, fileName, inputStream, null);
    }

    private void waitForVaultCredentials() {
        new Thread(() -> {
            boolean credentialsAvailable = false;
            while (!credentialsAvailable) {
                try {
                    this.s3Client = AmazonS3ClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(dynamicAWSCredentialsProvider.getCredentials()))
                            .build();
                    credentialsAvailable = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        Thread.sleep(1000); // Wait for 1 second before retrying
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }

}
