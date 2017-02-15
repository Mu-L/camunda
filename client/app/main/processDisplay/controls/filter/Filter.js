import {jsx, Scope, List, Match, Case} from 'view-utils';
import {DateFilter} from './DateFilter';

export function Filter() {
  return <td>
     <ul className="list-group filter-list">
       <Scope selector={getFilter}>
         <List>
          <Match>
            <Case predicate={isType('startDate')}>
              <DateFilter selector="data" />
            </Case>
          </Match>
         </List>
       </Scope>
    </ul>
  </td>;

  function isType(targetType) {
    return ({type}) => type === targetType;
  }

  function getFilter({filter: {query}}) {
    return query;
  }
}
