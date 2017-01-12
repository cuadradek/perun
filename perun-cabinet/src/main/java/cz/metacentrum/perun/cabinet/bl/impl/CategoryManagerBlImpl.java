package cz.metacentrum.perun.cabinet.bl.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.metacentrum.perun.cabinet.dao.CategoryManagerDao;
import cz.metacentrum.perun.cabinet.model.Author;
import cz.metacentrum.perun.cabinet.model.Category;
import cz.metacentrum.perun.cabinet.model.Publication;
import cz.metacentrum.perun.cabinet.bl.CabinetException;
import cz.metacentrum.perun.cabinet.bl.AuthorManagerBl;
import cz.metacentrum.perun.cabinet.bl.AuthorshipManagerBl;
import cz.metacentrum.perun.cabinet.bl.CategoryManagerBl;
import cz.metacentrum.perun.cabinet.bl.PerunManagerBl;
import cz.metacentrum.perun.cabinet.bl.PublicationManagerBl;
import cz.metacentrum.perun.core.api.PerunSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Class for handling Category entity in Cabinet.
 *
 * @author Jiri Harazim <harazim@mail.muni.cz>
 * @author Pavel Zlamal <256627@mail.muni.cz>
 */
public class CategoryManagerBlImpl implements CategoryManagerBl {

	private CategoryManagerDao categoryManagerDao;
	private PublicationManagerBl publicationService;
	private PerunManagerBl perunService;
	private AuthorshipManagerBl authorshipService;
	private AuthorManagerBl authorService;

	private static Logger log = LoggerFactory.getLogger(CategoryManagerBlImpl.class);

	// setters ----------------------

	@Autowired
	public void setCategoryManagerDao(CategoryManagerDao categoryManagerDao) {
		this.categoryManagerDao = categoryManagerDao;
	}

	public void setPublicationService(PublicationManagerBl publicationService) {
		this.publicationService = publicationService;
	}

	public void setPerunService(PerunManagerBl perunService) {
		this.perunService = perunService;
	}

	public void setAuthorshipService(AuthorshipManagerBl authorshipService) {
		this.authorshipService = authorshipService;
	}

	public void setAuthorService(AuthorManagerBl authorService) {
		this.authorService = authorService;
	}

	public CategoryManagerDao getCategoryManagerDao() {
		return categoryManagerDao;
	}

	// methods ----------------------

	@Override
	public Category createCategory(PerunSession sess, Category category) throws CabinetException, InternalErrorException {
		Category newCategory = getCategoryManagerDao().createCategory(sess, category);
		log.debug("{} created.", newCategory);
		return newCategory;
	}

	@Override
	public Category updateCategory(PerunSession sess, Category category) throws InternalErrorException, CabinetException {
		// save original category
		Category cat = getCategoryManagerDao().getCategoryById(category.getId());
		// update
		Category result = getCategoryManagerDao().updateCategory(sess, category);
		// was rank changed ?
		if (!Objects.equals(cat.getRank(), category.getRank())) {
			// yes
			Publication filter = new Publication();
			filter.setCategoryId(category.getId());
			List<Publication> pubs = publicationService.findPublicationsByFilter(filter);

			// update coef for all authors of all publications in updated category
			Set<Author> authors = new HashSet<Author>();
			for (Publication p : pubs) {
				authors.addAll(authorService.findAuthorsByPublicationId(p.getId()));
			}
			for (Author a : authors) {
				perunService.updatePriorityCoefficient(sess, a.getId(), authorshipService.calculateNewRank(a.getAuthorships()));
			}
			log.debug("Category: [{}] updated to Category: [{}]", cat, category);
		}
		return result;
	}

	@Override
	public void deleteCategory(PerunSession sess, Category category) throws InternalErrorException, CabinetException {
		getCategoryManagerDao().deleteCategory(sess, category);
		log.debug("{} deleted.", category);
	}

	@Override
	public List<Category> getCategories() throws InternalErrorException {
		return getCategoryManagerDao().getCategories();
	}

	@Override
	public Category getCategoryById(int id) throws CabinetException, InternalErrorException {
		return getCategoryManagerDao().getCategoryById(id);
	}

}
