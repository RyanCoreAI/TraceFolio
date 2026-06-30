package com.yanxai.tracefolio.source;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class BlogArticleLoader {

    public RawDocument load(String url) {
        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .userAgent("TraceFolioBot/0.1")
                    .timeout(10_000)
                    .get();

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
