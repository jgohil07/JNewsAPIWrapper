package com.main.java.startup;

import com.main.java.aggreators.ArticleAggreator;
import com.main.java.aggreators.SourcesAggreator;
import com.main.java.base.Constants;
import com.main.java.models.Articles;
import com.main.java.models.Sources;

/**
 * @author jgohil
 *
 */
public class InitializeNewsWrapper extends Constants {
	
	/**
	 * 
	 * Use it to know how wrapper works
	 * Uncomment printing statements in below methods to see sources, categories and articles 
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		testWrapper();
	}

	/**
	 * Start test bed
	 */
	public static void testWrapper() {

		//1st way to use Categories --> Sources --> Articles
		for(int i=0;i<categorySupported.length;i++) {
			ArticleAggreator aa = new ArticleAggreator();
			System.out.println("Category - " + categorySupported[i]);
			Sources scc = readSourcesFromCategory(categorySupported[i]);
			for(int k=0;k<scc.getSources().size();k++) {
				//Articles acc = readArticlebySource(scc.getSources().get(k).getId());
				Articles acc = aa.getArticlesForSource(scc.getSources().get(k).getId());
				for (int j = 0; j < acc.getArticles().size(); j++) {
					System.out.println("Article -> Title: " + acc.getArticles().get(j).getTitle() + ", Description: " + acc.getArticles().get(j).getDescription());
				}
			}
		}

		//2nd way to usw All Sources --> Articles
		Sources scc1 = readAllSources();
		ArticleAggreator aa = new ArticleAggreator();
		for(int k=0;k<scc1.getSources().size();k++) {
			//Articles acc = readArticlebySource(scc.getSources().get(k).getId());
			Articles acc = aa.getArticlesForSource(scc1.getSources().get(k).getId());
			for (int j = 0; j < acc.getArticles().size(); j++) {
				System.out.println("Article --> Title: " + acc.getArticles().get(j).getTitle() + ", Description: " + acc.getArticles().get(j).getDescription());
			}
		}
	}

	/**
	 * Returns all sources for all categories
	 * 
	 * @return
	 */
	public static Sources readAllSources() {
		SourcesAggreator sca = new SourcesAggreator();
		Sources sc = sca.getAllSources();
		/*System.out.println("Sources Size: " + sc.getSources().size());
		for(int i = 0; i< sc.getSources().size(); i++) {
			System.out.println("Source - Name: " + sc.getSources().get(i).getName() + ", id: " + sc.getSources().get(i).getId());
		}*/
		return sc;
	}

	/**
	 * Returns all sources in category passed as param
	 * 
	 * @param category
	 * @return
	 */
	public static Sources readSourcesFromCategory(String category) {
		SourcesAggreator sca = new SourcesAggreator();
		Sources sc = sca.getSourcesForCategory(baseURI + apiVersion + sourceURIPath, apiKey, languageDefault, countryDefault, category);
		/*System.out.println("Sources Size: " + sc.getSources().size());
		for(int i = 0; i< sc.getSources().size(); i++) {
			System.out.println("Source - Name: " + sc.getSources().get(i).getName() + ", id: " + sc.getSources().get(i).getId());
		}*/
		return sc;
	}

}
