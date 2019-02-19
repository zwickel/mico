.. java:import:: com.fasterxml.jackson.databind DeserializationFeature

.. java:import:: com.fasterxml.jackson.databind JsonNode

.. java:import:: com.fasterxml.jackson.databind ObjectMapper

.. java:import:: io.github.ust.mico.core.model MicoService

.. java:import:: io.github.ust.mico.core.model MicoServiceCrawlingOrigin

.. java:import:: lombok.extern.slf4j Slf4j

.. java:import:: org.springframework.boot.web.client RestTemplateBuilder

.. java:import:: org.springframework.http ResponseEntity

.. java:import:: org.springframework.web.client RestTemplate

.. java:import:: java.io IOException

.. java:import:: java.util ArrayList

.. java:import:: java.util List

GitHubCrawler
=============

.. java:package:: io.github.ust.mico.core.service
   :noindex:

.. java:type:: @Slf4j public class GitHubCrawler

Constructors
------------
GitHubCrawler
^^^^^^^^^^^^^

.. java:constructor:: public GitHubCrawler(RestTemplateBuilder restTemplateBuilder)
   :outertype: GitHubCrawler

Methods
-------
crawlGitHubRepoAllReleases
^^^^^^^^^^^^^^^^^^^^^^^^^^

.. java:method:: public List<MicoService> crawlGitHubRepoAllReleases(String uri)
   :outertype: GitHubCrawler

crawlGitHubRepoLatestRelease
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. java:method:: public MicoService crawlGitHubRepoLatestRelease(String uri)
   :outertype: GitHubCrawler

crawlGitHubRepoSpecificRelease
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. java:method:: public MicoService crawlGitHubRepoSpecificRelease(String uri, String version)
   :outertype: GitHubCrawler

makeUriToMatchGitHubApi
^^^^^^^^^^^^^^^^^^^^^^^

.. java:method:: public String makeUriToMatchGitHubApi(String uri)
   :outertype: GitHubCrawler
