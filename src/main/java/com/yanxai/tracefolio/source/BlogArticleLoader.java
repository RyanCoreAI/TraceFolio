package com.yanxai.tracefolio.source;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BlogArticleLoader {

    private static final int MAX_BODY_BYTES = 2_000_000;
    private final BlogDestinationPolicy destinationPolicy;

    @Autowired
    public BlogArticleLoader(@Value("${tracefolio.blog.allowed-hosts:}") String allowedHosts) {
        this.destinationPolicy = new BlogDestinationPolicy(allowedHosts);
    }

    public BlogArticleLoader() {
        this("");
    }

    public RawDocument load(String url) {
        try {
            URI destination = destinationPolicy.validate(url);
            org.jsoup.nodes.Document doc = null;
            for (int redirect = 0; redirect <= 5; redirect++) {
                var response = Jsoup.connect(destination.toString())
                        .userAgent("TraceFolioBot/0.1").timeout(10_000)
                        .maxBodySize(MAX_BODY_BYTES).followRedirects(false).execute();
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.header("Location");
                    if (location == null || redirect == 5) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "博客重定向无效或过多。");
                    }
                    destination = destinationPolicy.validate(destination.resolve(location).toString());
                    continue;
                }
                if (response.bodyAsBytes().length >= MAX_BODY_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "博客正文超过读取上限。");
                }
                doc = response.parse();
                break;
            }
            if (doc == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "博客内容不可用。");

            String title = firstNonBlank(
                    doc.select("meta[property=og:title]").attr("content"),
                    doc.title(),
                    url
            );

            Element article = doc.selectFirst("article");
            String text = article == null ? doc.body().text() : article.text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Empty article body: " + url);
            }

            return new RawDocument(
                    UUID.randomUUID(),
                    "BLOG",
                    title,
                    url,
                    text,
                    Map.of("url", url, "title", title)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load blog article: " + url, e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
