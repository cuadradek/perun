package cz.metacentrum.perun.cabinet.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.metacentrum.perun.cabinet.dao.AuthorshipManagerDao;
import cz.metacentrum.perun.cabinet.dao.mybatis.AuthorshipExample;
import cz.metacentrum.perun.cabinet.dao.mybatis.AuthorshipMapper;
import cz.metacentrum.perun.cabinet.model.Author;
import cz.metacentrum.perun.cabinet.model.Authorship;
import cz.metacentrum.perun.cabinet.bl.SortParam;
import cz.metacentrum.perun.cabinet.model.Category;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.impl.Compatibility;
import cz.metacentrum.perun.core.impl.Utils;
import org.springframework.jdbc.core.JdbcPerunTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;

/**
 * Class of DAO layer for handling Authorship entity.
 * Provides connection to proper mapper.
 *
 * @author Jiri Harazim <harazim@mail.muni.cz>
 */
public class AuthorshipManagerDaoImpl implements AuthorshipManagerDao {

	private JdbcPerunTemplate jdbc;

	private final static String AUTHORSHIP_SELECT_QUERY = "cabinet_categories.id as category_id, " +
			"cabinet_categories.name as category_name, cabinet_categories.rank as category_rank";

	private final static RowMapper<Category> AUTHORSHIP_ROW_MAPPER = new RowMapper<Category>() {
		@Override
		public Category mapRow(ResultSet resultSet, int i) throws SQLException {
			Category category = new Category();
			category.setId(resultSet.getInt("category_id"));
			category.setName(resultSet.getString("category_name"));
			category.setRank(resultSet.getDouble("category_rank"));
			return category;
		}
	};

	private static final String CREATED_DATE_DESC = "createdDate DESC";
	private static final String DESC = "DESC";
	private static final String ASC = "ASC";
	private AuthorshipMapper authorshipMapper;

	public AuthorshipManagerDaoImpl(DataSource perunPool) {
		this.jdbc = new JdbcPerunTemplate(perunPool);
	}

	// setters ----------------------

	public void setAuthorshipMapper(AuthorshipMapper authorshipMapper) {
		this.authorshipMapper = authorshipMapper;
	}

	// methods ----------------------

	@Override
	public Authorship createAuthorship(PerunSession sess, Authorship authorship) throws InternalErrorException {
		try {
			// Set the new Authorship id
			int newId = Utils.getNewId(jdbc, "cabinet_authorships_id_seq");
			jdbc.update("insert into cabinet_authorships (id, userId, publicationId, createdBy, createdDate, created_by_uid, modified_by_uid)" +
							" values (?,?,?,?," + Compatibility.getSysdate() + ",?,?)", newId, authorship.getUserId(), authorship.getPublicationId(),
					sess.getPerunPrincipal().getActor(), sess.getPerunPrincipal().getUserId(), sess.getPerunPrincipal().getUserId());
			authorship.setId(newId);
		} catch (RuntimeException e) {
			throw new InternalErrorException(e);
		}
		return authorship;
	}

	public List<Authorship> findByFilter(Authorship filter) {
		return authorshipMapper.findByFilter(filter);
	}


	public Authorship findById(Integer id) {
		return authorshipMapper.selectByPrimaryKey(id);
	}


	public Authorship findLastestOfUser(Integer userId) {
		AuthorshipExample example = new AuthorshipExample();
		example.createCriteria().andUserIdEqualTo(userId);
		example.setOrderByClause(CREATED_DATE_DESC);
		List<Authorship> reports = authorshipMapper.selectByExample(example);
		return (reports.size() > 0) ? reports.get(0) : null;
	}


	public List<Authorship> findAll() {
		List<Authorship> reports = authorshipMapper.selectByExample(null);
		return reports;
	}


	public int getCount() {
		int result = authorshipMapper.countByExample(null);
		return result;
	}

	public int getCountForUser(Integer userId) {
		AuthorshipExample example = new AuthorshipExample();
		example.createCriteria().andUserIdEqualTo(userId);
		int result = authorshipMapper.countByExample(example);
		return result;
	}

	public List<Authorship> findByFilter(Authorship filter, SortParam sortParam) {

		if (sortParam == null) {
			return findByFilter(filter);
		}

		Map<String, Object> params = new HashMap<String, Object>();
		params.put("id", filter.getId());
		params.put("userId", filter.getUserId());
		params.put("createdBy", filter.getCreatedBy());
		params.put("createdDate", filter.getCreatedDate());
		params.put("publicationId", filter.getPublicationId());

		params.put("orderProperty", sortParam.getProperty());//property must match column in db, watch JOIN sql!
		params.put("orderByClause", sortParam.getProperty() + " " + ((sortParam.isAscending()) ? ASC : DESC));
		params.put("order", (sortParam.isAscending() ? ASC : DESC));
		if (sortParam.getPage() != null && sortParam.getSize() != null) {
			int limit1 = sortParam.getPage() * sortParam.getSize();
			params.put("limit1", limit1);
			params.put("limit2", sortParam.getSize());
		}

		List<Authorship> r = authorshipMapper.findByParams(params);
		return r;
	}

	public List<Authorship> findByPublicationId(Integer id) {

		AuthorshipExample example = new AuthorshipExample();
		example.createCriteria().andPublicationIdEqualTo(id);
		return authorshipMapper.selectByExample(example);

	}

	public List<Authorship> findByUserId(Integer id) {

		AuthorshipExample example = new AuthorshipExample();
		example.createCriteria().andUserIdEqualTo(id);
		return authorshipMapper.selectByExample(example);

	}


	public int update(Authorship report) {
		return authorshipMapper.updateByPrimaryKey(report);
	}


	public int deleteById(Integer id) {
		return authorshipMapper.deleteByPrimaryKey(id);
	}

	public List<Integer> findUniqueAuthorsIds() {
		return authorshipMapper.selectUniqueAuthorsIds();
	}

	public Author findAuthorByUserId(Integer userId) {
		return authorshipMapper.findAuthorByUserId(userId);
	}

	public List<Author> findAuthorsByPublicationId(Integer publicationId) {
		return authorshipMapper.findAuthorsByPublicationId(publicationId);
	}

	public List<Author> findAllAuthors() {
		return authorshipMapper.findAllAuthors();
	}

}
