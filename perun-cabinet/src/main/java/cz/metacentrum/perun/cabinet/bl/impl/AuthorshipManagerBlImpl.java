package cz.metacentrum.perun.cabinet.bl.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import cz.metacentrum.perun.cabinet.dao.AuthorshipManagerDao;
import cz.metacentrum.perun.cabinet.model.Author;
import cz.metacentrum.perun.cabinet.model.Authorship;
import cz.metacentrum.perun.cabinet.model.Category;
import cz.metacentrum.perun.cabinet.model.Publication;
import cz.metacentrum.perun.cabinet.bl.CabinetException;
import cz.metacentrum.perun.cabinet.bl.ErrorCodes;
import cz.metacentrum.perun.cabinet.bl.AuthorManagerBl;
import cz.metacentrum.perun.cabinet.bl.AuthorshipManagerBl;
import cz.metacentrum.perun.cabinet.bl.CategoryManagerBl;
import cz.metacentrum.perun.cabinet.bl.PerunManagerBl;
import cz.metacentrum.perun.cabinet.bl.PublicationManagerBl;
import cz.metacentrum.perun.cabinet.bl.SortParam;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.Role;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.core.api.AuthzResolver;
import cz.metacentrum.perun.core.bl.PerunBl;

/**
 * Class for handling Authorship entity in Cabinet.
 *
 * @author Jiri Harazim <harazim@mail.muni.cz>
 * @author Pavel Zlamal <256627@mail.muni.cz>
 */
public class AuthorshipManagerBlImpl implements AuthorshipManagerBl {

	private static final double DEFAULT_RANK = 1.0;
	private AuthorshipManagerDao authorshipManagerDao;
	private PublicationManagerBl publicationService;
	private CategoryManagerBl categoryManagerBl;
	private AuthorManagerBl authorService;
	private PerunManagerBl perunService;
	private static Logger log = LoggerFactory.getLogger(AuthorshipManagerBlImpl.class);

	@Autowired
	private PerunBl perun;

	// setters ===========================================

	public void setAuthorService(AuthorManagerBl authorService) {
		this.authorService = authorService;
	}

	public void setPerunService(PerunManagerBl perunService) {
		this.perunService = perunService;
	}

	public void setPublicationService(PublicationManagerBl publicationService) {
		this.publicationService = publicationService;
	}

	@Autowired
	public void setAuthorshipManagerDao(AuthorshipManagerDao authorshipManagerDao) {
		this.authorshipManagerDao = authorshipManagerDao;
	}

	@Autowired
	public void setCategoryManagerBl(CategoryManagerBl categoryManagerBl) {
		this.categoryManagerBl = categoryManagerBl;
	}

	public AuthorshipManagerDao getAuthorshipManagerDao() {
		return authorshipManagerDao;
	}

	public CategoryManagerBl getCategoryManagerBl() {
		return categoryManagerBl;
	}


	// business methods ===================================


	public Authorship createAuthorship(PerunSession sess, Authorship authorship) throws CabinetException, InternalErrorException {

		if (authorshipExists(authorship)) throw new CabinetException(ErrorCodes.AUTHORSHIP_ALREADY_EXISTS);
		if (authorship.getCreatedDate() == null) {
			authorship.setCreatedDate(new Date());
		}
		if (authorship.getCreatedByUid() == null) {
			authorship.setCreatedByUid(sess.getPerunPrincipal().getUserId());
		}
		try {
			getAuthorshipManagerDao().createAuthorship(sess, authorship);
		} catch (DataIntegrityViolationException e) {
			throw new CabinetException(ErrorCodes.USER_NOT_EXISTS, e);
		}
		log.debug("Authorship: [{}] created.", authorship);
		// log
		try {
			perun.getAuditer().log(sess, "Authorship {} created.", authorship);
		} catch (InternalErrorException ex) {
			log.error("Unable to log message authorship created to Auditer for");
		}
		perunService.updatePriorityCoefficient(sess, authorship.getUserId(), calculateNewRank(authorship.getUserId()));

		perunService.setThanksAttribute(authorship.getUserId());

		return authorship;

	}


	public boolean authorshipExists(Authorship authorship) {
		if (authorship == null) throw new NullPointerException("Authorship cannot be null");

		if (authorship.getId() != 0) {
			return getAuthorshipManagerDao().findById(authorship.getId()) != null;
		}
		if (authorship.getPublicationId() != null && authorship.getUserId() != null) {
			Authorship filter = new Authorship();
			filter.setPublicationId(authorship.getPublicationId());
			filter.setUserId(authorship.getUserId());
			return getAuthorshipManagerDao().findByFilter(filter).size() > 0;
		}
		return false;
	}

	public Double calculateNewRank(Integer userId) throws CabinetException, InternalErrorException {

		List<Authorship> reports = findAuthorshipsByUserId(userId);
		return calculateNewRank(reports);

	}

	public synchronized Double calculateNewRank(List<Authorship> authorships) throws InternalErrorException, CabinetException {

		Double rank = DEFAULT_RANK;
		for (Authorship r : authorships) {
			Publication p = publicationService.findPublicationById(r.getPublicationId());
			rank += p.getRank();
			Category c = getCategoryManagerBl().getCategoryById(p.getCategoryId());
			rank += c.getRank();
		}
		return rank;

	}

	public List<Authorship> findAuthorshipsByFilter(Authorship filter) {
		return getAuthorshipManagerDao().findByFilter(filter);
	}

	public Date getLastCreatedAuthorshipDate(Integer userId) {
		Authorship report = getAuthorshipManagerDao().findLastestOfUser(userId);
		return (report != null) ? report.getCreatedDate() : null;
	}

	public List<Author> findAuthorsByAuthorshipId(PerunSession sess, Integer id) throws CabinetException {
		List<Author> result = new ArrayList<Author>();

		Authorship report = getAuthorshipManagerDao().findById(id);
		if (report == null) {
			throw new CabinetException("Authorship with ID: "+id+" doesn't exists!", ErrorCodes.AUTHORSHIP_NOT_EXISTS);
		}

		Authorship filter = new Authorship();
		filter.setPublicationId(report.getPublicationId());

		List<Authorship> publicationReports = getAuthorshipManagerDao().findByFilter(filter, null);

		for (Authorship r : publicationReports) {
			result.add(authorService.findAuthorByUserId(r.getUserId()));
		}
		return result;
	}

	public List<Authorship> findAllAuthorships() {
		return getAuthorshipManagerDao().findAll();
	}


	public int getAuthorshipsCount() {
		return getAuthorshipManagerDao().getCount();
	}

	public int getAuthorshipsCountForUser(Integer userId) {
		return getAuthorshipManagerDao().getCountForUser(userId);
	}

	public List<Authorship> findAuthorshipsByFilter(Authorship report, SortParam sortParam) {
		if (sortParam == null) return findAuthorshipsByFilter(report);
		if (! sortParam.getProperty().toString().matches("[a-z,A-Z,_,0-9]*")) throw new IllegalArgumentException("sortParam.property is not allowed: "+sortParam.getProperty());
		return getAuthorshipManagerDao().findByFilter(report, sortParam);
	}


	public Authorship findAuthorshipById(Integer id) {
		return getAuthorshipManagerDao().findById(id);
	}


	public List<Authorship> findAuthorshipsByPublicationId(Integer id) {
		return getAuthorshipManagerDao().findByPublicationId(id);
	}


	public List<Authorship> findAuthorshipsByUserId(Integer id) {
		return getAuthorshipManagerDao().findByUserId(id);
	}


	public int updateAuthorship(PerunSession sess, Authorship report) throws CabinetException, InternalErrorException {

		// check if such authorship exists
		Authorship r = getAuthorshipManagerDao().findById(report.getId());
		if (r == null) {
			throw new CabinetException("Authorship with ID: "+report.getId()+" doesn't exists.", ErrorCodes.AUTHORSHIP_NOT_EXISTS);
		}
		// check if "new" authorship already exist before update
		Authorship filter = new Authorship();
		filter.setPublicationId(report.getPublicationId());
		filter.setUserId(report.getUserId());
		List<Authorship> list = getAuthorshipManagerDao().findByFilter(filter);
		for (Authorship a : list) {
			if (a.getId() != report.getId()) {
				throw new CabinetException("Can't update authorship ID="+report.getId()+", same authorship already exists under ID="+a.getId(), ErrorCodes.AUTHORSHIP_ALREADY_EXISTS);
			}
		}
		// update
		int rows = getAuthorshipManagerDao().update(report);

		// if updated
		if (rows > 0) {
			if (report.getPublicationId() != r.getPublicationId()) {
				// If authorship moved to another publication
				Set<Author> authors = new HashSet<Author>();
				// get authors of both publications
				authors.addAll(authorService.findAuthorsByPublicationId(report.getPublicationId()));
				authors.addAll(authorService.findAuthorsByPublicationId(r.getPublicationId()));
				// process them
				for (Author a : authors) {
					perunService.updatePriorityCoefficient(sess, a.getId(), calculateNewRank(a.getAuthorships()));
				}
				// calculate thanks for original user
				perunService.setThanksAttribute(r.getUserId());
				if (r.getUserId() != report.getUserId()) {
					// if user changed along side publication - calculate thanks for second user
					perunService.setThanksAttribute(report.getUserId());
				}
			} else if (r.getUserId() != report.getUserId()) {
				// if user (author) changed, update for both of them
				perunService.updatePriorityCoefficient(sess, report.getUserId(), calculateNewRank(report.getUserId()));
				perunService.setThanksAttribute(report.getId());
				perunService.updatePriorityCoefficient(sess, report.getUserId(), calculateNewRank(r.getUserId()));
				perunService.setThanksAttribute(r.getUserId());
			}
			log.debug("Authorship: [{}] updated to Authorship: [{}].", r, report);
		}
		return rows;

	}


	public int deleteAuthorshipById(PerunSession sess, Integer id) throws CabinetException, InternalErrorException {

		Authorship a = findAuthorshipById(id);
		if (a == null) {
			throw new CabinetException("Authorship with ID: "+id+" doesn't exists.", ErrorCodes.AUTHORSHIP_NOT_EXISTS);
		}
		// authorization TODO - better place ??
		// To delete authorship user must me either PERUNADMIN
		// or user who created record (authorship.createdBy property)
		// or user which is concerned by record (authorship.userId property)
		try {
			if (!AuthzResolver.isAuthorized(sess, Role.PERUNADMIN) &&
					!a.getCreatedBy().equalsIgnoreCase(sess.getPerunPrincipal().getActor()) &&
					!a.getUserId().equals(sess.getPerunPrincipal().getUser().getId()) &&
					!a.getCreatedByUid().equals(sess.getPerunPrincipal().getUserId())) {
				throw new CabinetException("You are not allowed to delete authorships you didn't created or which doesn't concern you.", ErrorCodes.NOT_AUTHORIZED);
					}
		} catch (PerunException pe) {
			throw new CabinetException(ErrorCodes.PERUN_EXCEPTION, pe);
		}
		// delete
		int rows = getAuthorshipManagerDao().deleteById(id);

		// if deleted
		if (rows > 0) {
			// update coefficient
			int userId = a.getUserId();
			perunService.updatePriorityCoefficient(sess, userId, calculateNewRank(userId));
			log.debug("Authorship: [{}] deleted.", a);
			try {
				perun.getAuditer().log(sess, "Authorship {} deleted.", a);
			} catch (InternalErrorException ex) {
				log.error("Unable to log message authorship deleted to Auditer.");
			}

			perunService.setThanksAttribute(a.getUserId());

		}

		return rows;

	}

}
