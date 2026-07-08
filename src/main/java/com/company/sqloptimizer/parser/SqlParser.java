package com.company.sqloptimizer.parser;

import java.util.Set;

public interface SqlParser {

    ParsedQuery parse(String sql);

}
