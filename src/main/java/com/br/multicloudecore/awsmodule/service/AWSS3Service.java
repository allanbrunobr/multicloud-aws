package com.br.multicloudecore.awsmodule.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.br.multicloudecore.awsmodule.config.AWSConfig;
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
public class AWSS3Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(AWSS3Service.class);
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "pdf", "txt");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB


    private AWSCredentialsProvider awsCredentialsProvider;

    @Value("${spring.cloud.aws.s3.bucket-name}")
    private String bucketName;

    public void storeFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            LOGGER.error("File extension '{}' is not allowed for upload", extension);
            throw new IllegalArgumentException("File type not allowed");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            LOGGER.error("File size {} exceeds the maximum allowed size of {}", file.getSize(), MAX_FILE_SIZE);
            throw new IllegalArgumentException("File size exceeds the maximum allowed size");
        }

        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(awsCredentialsProvider)
                .build();
        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            PutObjectRequest request = new PutObjectRequest(
                    bucketName,
                    file.getOriginalFilename(),
                    inputStream, metadata);

             s3.putObject(request);

        } catch (AmazonClientException e) {
            LOGGER.error("Error occurred during S3 file upload", e);
        } catch (IOException e) {
            LOGGER.error("Error occurred while reading file for S3 upload", e);
        }
    }

    public void uploadPhoto(BufferedImage image, String fileName) throws IOException {
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(awsCredentialsProvider)
                .build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", os);
        byte[] imageBytes = os.toByteArray();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);

        s3.putObject(bucketName, fileName, inputStream, null);
    }
}