package com.lipata.forkauthority.poll

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lipata.forkauthority.ForkAuthorityApp
import com.lipata.forkauthority.R
import com.lipata.forkauthority.data.Lce
import com.lipata.forkauthority.data.user.UserIdentityManager
import kotlinx.android.synthetic.main.fragment_view_poll.*
import kotlinx.android.synthetic.main.fragment_view_poll.view.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class ViewPollFragment : Fragment(), VotableRestaurantListener {

    @Inject
    lateinit var userIdentityManager: UserIdentityManager

    @Inject
    lateinit var viewModel: PollViewModel

    lateinit var adapter: VotableRestaurantsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as ForkAuthorityApp).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_view_poll, container, false)

        viewModel.run {
            arguments?.let {
                documentId = ViewPollFragmentArgs.fromBundle(it).documentId
            }
            lifecycle.addObserver(this)
            observeLiveData(this@ViewPollFragment, Observer { onLce(it) })
            adapter = VotableRestaurantsAdapter(this@ViewPollFragment)
        }

        view.pollRecycler.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ViewPollFragment.adapter
        }

        view.tvAddRestaurant.setOnClickListener { onAddRestaurant() }

        return view
    }

    private fun onAddRestaurant() {
        RestaurantDialog().showRestaurantPrompt(requireContext(),
            object : RestaurantDialog.Listener {
                override fun onSubmit(text: String) {
                    lifecycleScope.launch {
                        viewModel.addVotableRestaurant(text)
                    }
                }
            })
    }

    private fun onLce(lce: Lce?) {
        when (lce) {
            is Lce.Loading -> {
            } // not used

            is Lce.Error -> {
                Snackbar.make(pollRecycler, "Error ${lce.error}", Snackbar.LENGTH_LONG).show()
            }

            is Lce.Content<*> -> {
                adapter.items = lce.content as? List<VotableRestaurant>
                    ?: emptyList() // TODO these generics are not great
                adapter.notifyDataSetChanged()
            }

            null -> {
            } // do nothing
        }

    }

    override fun voteFor(data: VotableRestaurant, position: Int) {
        lifecycleScope.launch {
            viewModel.voteFor(data, position)
        }
    }

    override fun voteAgainst() {
    }
}
